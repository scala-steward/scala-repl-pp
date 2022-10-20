package object replpp {
  def compilerArgs(config: Config): Array[String] = {
    val compilerArgs = Array.newBuilder[String]

    val dependencyFiles = Dependencies.resolveOptimistically(config.dependencies, config.verbose)
    compilerArgs ++= Array("-classpath", replClasspath(dependencyFiles))
    compilerArgs += "-explain" // verbose scalac error messages
    compilerArgs += "-deprecation"
    if (config.nocolors) compilerArgs ++= Array("-color", "never")
    compilerArgs.result()
  }

  private def replClasspath(dependencies: Seq[java.io.File]): String = {
    val inheritedClasspath = System.getProperty("java.class.path")
    val separator = System.getProperty("path.separator")

    val entriesForDeps = dependencies.mkString(separator)
    s"$inheritedClasspath$separator$entriesForDeps"
  }

  def readPredefFiles(files: Seq[os.Path]): Seq[String] =
    files.flatMap { file =>
      assert(os.exists(file), s"$file does not exist")
      val lines = os.read.lines(file)
      println(s"importing $file (${lines.size} lines) from $file")
      lines
    }

}
