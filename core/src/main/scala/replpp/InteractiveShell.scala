package replpp

import dotty.tools.repl.State

import java.lang.System.lineSeparator
import scala.util.control.NoStackTrace

object InteractiveShell {

  def run(config: Config): Unit = {
    import config.colors
    val config0 = precompilePredefFiles(config)
    val compilerArgs = replpp.compilerArgs(config0)
    val verbose = verboseEnabled(config)
    val runBeforeLines = DefaultRunBeforeLines ++ globalRunBeforeLines ++ config.runBefore

    val replDriver = new ReplDriver(
      compilerArgs,
      greeting = config0.greeting,
      prompt = config0.prompt.getOrElse("scala"),
      maxHeight = config0.maxHeight,
      runAfter = config0.runAfter,
      verbose = verbose,
      lineNumberReportingAdjustment = -runBeforeLines.size
    )

    val initialState: State = replDriver.initialState
    val state: State = {
      val runBeforeLinesString = runBeforeLines.mkString(lineSeparator)
      if (verbose) {
        println(s"compiler arguments: ${compilerArgs.mkString(",")}")
        println(runBeforeLines)
        replDriver.run(runBeforeLinesString)(using initialState)
      } else {
        replDriver.runQuietly(runBeforeLinesString)(using initialState)
      }
    }

    if (runBeforeLines.nonEmpty && state.objectIndex != 1) {
      throw new RuntimeException(s"compilation error for predef code - error should have been reported above ^^^")
    }

    replDriver.runUntilQuit(using state)()
  }
  
}
