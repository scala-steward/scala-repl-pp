import replpp.util.{ClasspathHelper, SimpleDriver, linesFromFile}

import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable

package object replpp {
  enum Colors { case BlackWhite, Default }
  val VerboseEnvVar    = "SCALA_REPL_PP_VERBOSE"
  lazy val pwd: Path = Paths.get(".").toAbsolutePath
  lazy val home: Path = Paths.get(System.getProperty("user.home"))
  lazy val globalRunBeforeFile: Path = home.resolve(".scala-repl-pp.sc")
  lazy val globalRunBeforeFileMaybe: Option[Path] = Option(globalRunBeforeFile).filter(Files.exists(_))
  lazy val globalRunBeforeLines: Seq[String] = globalRunBeforeFileMaybe.map(linesFromFile).getOrElse(Seq.empty)

  private[replpp] def DefaultRunBeforeLines(using colors: Colors) = {
    val colorsImport = colors match {
      case Colors.BlackWhite => "replpp.Colors.BlackWhite"
      case Colors.Default => "replpp.Colors.Default"
    }
    Seq(
      "import replpp.Operators.*",
      s"given replpp.Colors = $colorsImport",
    )
  }

  /** verbose mode can either be enabled via the config, or the environment variable `SCALA_REPL_PP_VERBOSE=true` */
  def verboseEnabled(config: Config): Boolean = {
    config.verbose ||
      sys.env.get(VerboseEnvVar).getOrElse("false").toLowerCase.trim == "true"
  }

  def compilerArgs(config: Config): Array[String] = {
    val compilerArgs = Array.newBuilder[String]
    compilerArgs ++= Array("-classpath", ClasspathHelper.create(config))
    compilerArgs += "-explain" // verbose scalac error messages
    compilerArgs += "-deprecation"
    if (config.nocolors) compilerArgs ++= Array("-color", "never")

    compilerArgs.result()
  }

  /** recursively find all relevant source files from main script, global predef file, 
    * provided predef files, other scripts that were imported with `using file` directive */
  def allSourceFiles(config: Config): Seq[Path] =
    (allPredefFiles(config) ++ config.scriptFile ++ globalRunBeforeFileMaybe).distinct.sorted

  def allPredefFiles(config: Config): Seq[Path] = {
    val allPredefFiles  = mutable.Set.empty[Path]
    allPredefFiles ++= config.predefFiles

    // the directly referenced predef files might reference additional files via `using` directive
    val predefFilesDirect = allPredefFiles.toSet
    predefFilesDirect.foreach { predefFile =>
      val importedFiles = UsingDirectives.findImportedFilesRecursively(predefFile, visited = allPredefFiles.toSet)
      allPredefFiles ++= importedFiles
    }

    // the script (if any) might also reference additional files via `using` directive
    config.scriptFile.foreach { scriptFile =>
      val importedFiles = UsingDirectives.findImportedFilesRecursively(scriptFile, visited = allPredefFiles.toSet)
      allPredefFiles ++= importedFiles
    }

    allPredefFiles.toSeq.filter(Files.exists(_)).sorted
  }

  def allSourceLines(config: Config): Seq[String] =
    allSourceFiles(config).flatMap(linesFromFile) ++ config.runBefore

  /** precompile given predef files (if any) and update Config to include the results in the classpath */
  def precompilePredefFiles(config: Config): Config = {
    if (config.predefFiles.nonEmpty) {
      val predefClassfilesDir = new SimpleDriver().compileAndGetOutputDir(
        replpp.compilerArgs(config),
        inputFiles = allPredefFiles(config),
        verbose = config.verbose
      ).get
      config.withAdditionalClasspathEntry(predefClassfilesDir)
    } else config
  }

  /**
    * resolve absolute or relative paths to an absolute path
    * - if given pathStr is an absolute path, just take that
    * - if it's a relative path, use given base path to resolve it to an absolute path
    * - if the base path is a file, take it's root directory - anything else doesn't make any sense.
    */
  def resolveFile(base: Path, pathStr: String): Path = {
    val path = Paths.get(pathStr)
    if (path.isAbsolute) path
    else {
      val base0 =
        if (Files.isDirectory(base)) base
        else base.getParent
      base0.resolve(path)
    }
  }

}
