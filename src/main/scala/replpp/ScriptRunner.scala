package replpp

import dotty.tools.scripting.ScriptingDriver
import java.util.stream.Collectors
import scala.jdk.CollectionConverters._

object ScriptRunner {
  val UsingLibDirective = "//> using lib "
  
  def exec(config: Config): Unit = {
    val scriptFile = config.scriptFile.getOrElse(throw new AssertionError("scriptFile not defined"))
    if (!os.exists(scriptFile)) {
      throw new AssertionError(s"given script file $scriptFile does not exist")
    }

    System.err.println(s"executing $scriptFile with params=${config.params}")
    val scriptArgs: Seq[String] = {
      val commandArgs = config.command.toList
      val parameterArgs = config.params.flatMap { case (key, value) => Seq(s"--$key", value) }
      commandArgs ++ parameterArgs
    }

    // Our predef code includes import statements... I didn't find a nice way to add them to the context of the
    // script file, so instead we'll just write it to the beginning of the script file.
    // That's obviously suboptimal, e.g. because it messes with the line numbers.
    // Therefor, we'll display the temp script file name to the user and not delete it, in case the script errors.
    val predefCode = allPredefCode(config)
    val predefPlusScriptFileTmp = os.temp(prefix = "scala-repl-pp-script-with-predef", suffix = ".sc")
    val scriptCode = os.read(scriptFile)
    val scriptContent = wrapForMainargs(predefCode, scriptCode)
    if (config.verbose) println(scriptContent)
    os.write.over(predefPlusScriptFileTmp, scriptContent)

    new ScriptingDriver(
      compilerArgs = compilerArgs(parseUsingLibDirectives(predefCode, scriptCode, config)) :+ "-nowarn",
      scriptFile = predefPlusScriptFileTmp.toIO,
      scriptArgs = scriptArgs.toArray
    ).compileAndRun()
    System.err.println(s"script finished successfully")

    // if the script failed, the ScriptingDriver would have thrown an exception, in which case we
    // don't delete the temporary file which includes the predef,
    // so that the line numbers are accurate and the user can properly debug
    os.remove(predefPlusScriptFileTmp)
  }

  private def parseUsingLibDirectives(predefCode: String, scriptCode: String, config: Config): Config = {
    val dependenciesFromUsingClauses =
      s"""
         |$predefCode
         |$scriptCode
         |"""
        .stripMargin
        .lines()
        .map(_.trim)
        .filter(_.startsWith(UsingLibDirective))
        .map(_.drop(UsingLibDirective.length).trim)
        .collect(Collectors.toList)
        .asScala

    config.copy(dependencies = config.dependencies ++ dependenciesFromUsingClauses)
  }

  private def wrapForMainargs(predefCode: String, scriptCode: String): String = {
    val mainImpl =
      if (scriptCode.contains("@main")) {
        scriptCode
      } else {
        s"""@main def _execMain(): Unit = {
           |  $scriptCode
           |}
           |""".stripMargin
      }

    s"""
       |import mainargs.main // intentionally shadow any potentially given @main
       |
       |// dotty's ScriptingDriver expects an object with a `main(Array[String]): Unit`
       |object Main {
       |
       |$predefCode
       |
       |$mainImpl
       |
       |  def main(args: Array[String]): Unit = {
       |    mainargs.ParserForMethods(this).runOrExit(args.toSeq)
       |  }
       |}
       |""".stripMargin
  }

}
