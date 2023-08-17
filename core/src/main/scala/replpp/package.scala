import replpp.util.linesFromFile

import java.io.File
import java.lang.System.lineSeparator
import java.net.URL
import java.nio.file.{Files, Path, Paths}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.io.Source
import scala.util.Using

package object replpp {
  enum Colors { case BlackWhite, Default }

  /* ":" on unix */
  val pathSeparator = java.io.File.pathSeparator

  val VerboseEnvVar    = "SCALA_REPL_PP_VERBOSE"

  /** The user's home directory */
  lazy val home: Path = Paths.get(System.getProperty("user.home"))

  /** The current working directory for this process. */
  lazy val pwd: Path = Paths.get(".").toAbsolutePath

  lazy val globalPredefFile = home.resolve(".scala-repl-pp.sc")
  lazy val globalPredefFileMaybe = Option(globalPredefFile).filter(Files.exists(_))
  private[replpp] def DefaultPredefLines(using colors: Colors) = {
    val colorsImport = colors match {
      case Colors.BlackWhite => "replpp.Colors.BlackWhite"
      case Colors.Default => "replpp.Colors.Default"
    }
    Seq(
      "import replpp.Operators.*",
      s"given replpp.Colors = $colorsImport"
    )
  }
  private[replpp] def DefaultPredef(using Colors) = DefaultPredefLines.mkString(lineSeparator)

  /** verbose mode can either be enabled via the config, or the environment variable `SCALA_REPL_PP_VERBOSE=true` */
  def verboseEnabled(config: Config): Boolean = {
    config.verbose ||
      sys.env.get(VerboseEnvVar).getOrElse("false").toLowerCase.trim == "true"
  }

  def compilerArgs(config: Config): Array[String] = {
    val compilerArgs = Array.newBuilder[String]
    compilerArgs ++= Array("-classpath", classpath(config))
    compilerArgs += "-explain" // verbose scalac error messages
    compilerArgs += "-deprecation"
    if (config.nocolors) compilerArgs ++= Array("-color", "never")

    compilerArgs.result()
  }

  /**
   * Concatenates the classpath from multiple sources, each are required for different scenarios:
   * - `java.class.path` system property
   * - dependency artifacts as passed via (command-line) configuration
   * - jars from current class loader (recursively)
   *
   * To have reproducable results, we order the classpath entries. This may interfere with the user's deliberate choice
   * of order, but since we need to concatenate the classpath from different sources, the user can't really depend on
   * the order anyway.
   */
  def classpath(config: Config, quiet: Boolean = false): String = {
    val entries = Seq.newBuilder[String]
    System.getProperty("java.class.path").split(pathSeparator).foreach(entries.addOne)

    val fromDependencies = dependencyArtifacts(config)
    fromDependencies.foreach(file => entries.addOne(file.getPath))
    if (fromDependencies.nonEmpty && !quiet) {
      println(s"resolved dependencies - adding ${fromDependencies.size} artifact(s) to classpath - to list them, enable verbose mode")
      if (verboseEnabled(config)) fromDependencies.foreach(println)
    }

    jarsFromClassLoaderRecursively(classOf[replpp.ReplDriver].getClassLoader)
      .foreach(url => entries.addOne(url.getPath))

    /** Important: we absolutely have to make sure this ends with a `pathSeparator`.
     * Otherwise, the last entry is lost somewhere down the line. I'm still debugging where exactly, but it looks
     * like somewhere in dotty. Will report upstream once I figured it out, but for now it doesn't harm to add a
     * pathseparator at the end.
     */
    entries.result().distinct.sorted.mkString(pathSeparator, pathSeparator, pathSeparator)
  }

  private def dependencyArtifacts(config: Config): Seq[File] = {
    val scriptLines = config.scriptFile.map { path =>
       Using.resource(Source.fromFile(path.toFile))(_.getLines.toSeq)
    }.getOrElse(Seq.empty)
    val allLines = allPredefLines(config) ++ scriptLines

    val resolvers = config.resolvers ++ UsingDirectives.findResolvers(allLines)
    val allDependencies = config.dependencies ++ UsingDirectives.findDeclaredDependencies(allLines)
    Dependencies.resolve(allDependencies, resolvers).get
  }
  
  private def jarsFromClassLoaderRecursively(classLoader: ClassLoader): Seq[URL] = {
    classLoader match {
      case cl: java.net.URLClassLoader =>
        jarsFromClassLoaderRecursively(cl.getParent) ++ cl.getURLs
      case _ =>
        Seq.empty
    }
  }

  def allPredefCode(config: Config): String =
    allPredefLines(config).mkString(lineSeparator)

  def allPredefLines(config: Config): Seq[String] = {
    val resultLines = Seq.newBuilder[String]
    val visited = mutable.Set.empty[Path]
    import config.colors

    resultLines ++= DefaultPredefLines

    val allPredefFiles = globalPredefFileMaybe ++ config.predefFiles
    allPredefFiles.foreach { file =>
      val importedFiles = UsingDirectives.findImportedFilesRecursively(file, visited.toSet)
      visited ++= importedFiles
      importedFiles.foreach { file =>
        resultLines ++= linesFromFile(file)
      }

      resultLines ++= linesFromFile(file)
      visited += file
    }

    config.scriptFile.foreach { file =>
      val importedFiles = UsingDirectives.findImportedFilesRecursively(file, visited.toSet)
      visited ++= importedFiles
      importedFiles.foreach { file =>
        resultLines ++= linesFromFile(file)
      }
    }

    resultLines.result().filterNot(_.trim.startsWith(UsingDirectives.FileDirective))
  }

  /**
    * resolve absolute or relative paths to an absolute path
    * - if given pathStr is an absolute path, just take that
    * - if it's a relative path, use given base path to resolve it to an absolute path
    */
  def resolveFile(base: Path, pathStr: String): Path = {
    val path = Paths.get(pathStr)
    if (path.isAbsolute) path
    else base.resolve(path)
  }

}
