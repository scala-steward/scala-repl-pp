package replpp
package util

import java.io.File.pathSeparator
import java.net.URL
import java.nio.file.{Path, Paths}
import scala.io.Source
import scala.util.Using

object ClasspathHelper {

  /**
   * Concatenates the classpath from multiple sources, each are required for different scenarios:
   * - `java.class.path` system property
   * - jars from current class loader (recursively)
   * - dependency artifacts as passed via (command-line) configuration
   * The exact behaviour regarding inherited classpath (java.class.path and outer classloader) can be configured via `Config.ForClasspath
   *
   * To have reproducible results, we order the classpath entries. This may interfere with the user's deliberate choice
   * of order, but since we need to concatenate the classpath from different sources, the user can't really depend on
   * the order anyway.
   */
  def create(config: Config, quiet: Boolean = false): String =
    create(fromConfig(config, quiet).map(util.pathAsString))

  def create(entries: Seq[String]): String = {
    /** Important: we absolutely have to make sure this starts and ends with a `pathSeparator`.
     * Otherwise, the last entry is lost somewhere down the line (I didn't find the exact location where things go
     * wrong, but it looked like somewhere in dotty 3.3.0). */
    entries.distinct.mkString(pathSeparator, pathSeparator, pathSeparator)
  }

  protected[util] def fromConfig(config: Config, quiet: Boolean = false): Seq[Path] = {
    val entries = Seq.newBuilder[Path]
    val debugPrint = verboseEnabled(config) && !quiet

    // add select entries from out inherited classpath to the resulting classpath
    def addToEntriesMaybe(path: Path): Unit = {
      val classpathConfig = config.classpathConfig
      val filename = path.getFileName.toString
      val included = classpathConfig.inheritClasspath || classpathConfig.inheritClasspathIncludes.exists(filename.matches(_))
      lazy val excluded = classpathConfig.inheritClasspathExcludes.exists(filename.matches(_))
      if (included && !excluded) {
        if (debugPrint) println(s"using jar from inherited classpath: $path")
        entries.addOne(path)
      } else {
        if (debugPrint) println(s"excluding jar from inherited classpath (included=$included; excluded=$excluded: $path)")
      }
    }
    System.getProperty("java.class.path").split(pathSeparator).filter(_.nonEmpty).map(Paths.get(_)).foreach(addToEntriesMaybe)
    jarsFromClassLoaderRecursively(classOf[replpp.ReplDriver].getClassLoader).map(url => Paths.get(url.toURI)).foreach(addToEntriesMaybe)

    val fromDependencies = dependencyArtifacts(config)
    fromDependencies.foreach(entries.addOne)
    if (fromDependencies.nonEmpty && !quiet) {
      println(s"resolved dependencies - adding ${fromDependencies.size} artifact(s) to classpath - to list them, enable verbose mode")
      if (verboseEnabled(config)) fromDependencies.foreach(println)
    }

    entries.addAll(config.classpathConfig.additionalClasspathEntries.map(Paths.get(_)))
    entries.addAll(UsingDirectives.findClasspathEntriesInFiles(allSourceFiles(config)))

    val runBeforeCodeAsLines = config.runBefore.flatMap(_.linesIterator)
    entries.addAll(UsingDirectives.findClasspathEntriesInLines(runBeforeCodeAsLines, currentWorkingDirectory))

    val result = entries.result().distinct.sorted
    if (debugPrint) {
      println("classpath entries from config >")
      println(result.mkString("\n"))
      println("< classpath entries from config")
    }

    result
  }

  private[util] def dependencyArtifacts(config: Config): Seq[Path] = {
    val allLines = allSourceLines(config)
    val resolvers = config.classpathConfig.resolvers ++ UsingDirectives.findResolvers(allLines)
    val allDependencies = config.classpathConfig.dependencies ++ UsingDirectives.findDeclaredDependencies(allLines)
    Dependencies.resolve(allDependencies, resolvers, verboseEnabled(config)).get
  }

  private def jarsFromClassLoaderRecursively(classLoader: ClassLoader): Seq[URL] = {
    classLoader match {
      case cl: java.net.URLClassLoader =>
        jarsFromClassLoaderRecursively(cl.getParent) ++ cl.getURLs
      case _ =>
        Seq.empty
    }
  }

}
