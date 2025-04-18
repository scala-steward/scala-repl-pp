package replpp

import scala.language.unsafeNulls
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.parsing.Scanners.Scanner
import dotty.tools.dotc.parsing.Tokens.*
import dotty.tools.dotc.printing.SyntaxHighlighting
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.util.SourceFile
import dotty.tools.repl.ParseResult
import org.jline.reader
import org.jline.reader.Parser.ParseContext
import org.jline.reader.*
import org.jline.reader.impl.LineReaderImpl
import org.jline.reader.impl.history.DefaultHistory
import org.jline.terminal.Terminal
import org.jline.terminal.Terminal.Signal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.AttributedString

import scala.util.Try

/** Based on https://github.com/lampepfl/dotty/blob/3.4.1/compiler/src/dotty/tools/repl/JLineTerminal.scala
  * and adapted for our needs */
class JLineTerminal extends java.io.Closeable {

  // MP: adapted here
  // if env var TERM=dumb is defined, instantiate as 'dumb' straight away, otherwise try to instantiate as a
  // regular terminal and fallback to a dumb terminal if that's not possible
  private val terminal: Terminal = {
    var builder = TerminalBuilder.builder()
    if System.getenv("TERM") == "dumb" then
      // Force dumb terminal if `TERM` is `"dumb"`.
      // Note: the default value for the `dumb` option is `null`, which allows
      // JLine to fall back to a dumb terminal. This is different than `true` or
      // `false` and can't be set using the `dumb` setter.
      // This option is used at https://github.com/jline/jline3/blob/894b5e72cde28a551079402add4caea7f5527806/terminal/src/main/java/org/jline/terminal/TerminalBuilder.java#L528.
      builder.dumb(true)
    builder.build()
  }

  // MP: adapted here
  // ignore SIGINT (Ctrl-C)
  var lastIntSignalReceived = 0L
  terminal.handle(Signal.INT, _ =>
    // we can't easily cancel the current computation on the jvm, but we can stop long-running printing of outputs
    // in some situations they might want to kill the entire REPL  (e.g. if they're in an endless loop) - they can just
    // press Ctrl-C twice in that case (within 3 seconds)
    if (System.currentTimeMillis() - lastIntSignalReceived < 3000) {
      System.exit(130)
    } else {
      lastIntSignalReceived = System.currentTimeMillis()
      println("Captured interrupt signal `INT` - if you want to kill the REPL, press Ctrl-c again within three seconds")
    }
  )

  private val history = new DefaultHistory

  private def promptColor(str: String)(using Context) =
    if (ctx.settings.color.value != "never") Console.MAGENTA + str + Console.RESET
    else str
  protected def promptStr = "scala"
  private def prompt(using Context)        = promptColor(s"\n$promptStr> ")
  private def newLinePrompt(using Context) = promptColor("     | ")

  /** Blockingly read line from `System.in`
   *
   *  This entry point into JLine handles everything to do with terminal
   *  emulation. This includes:
   *
   *  - Multi-line support
   *  - Copy-pasting
   *  - History
   *  - Syntax highlighting
   *  - Auto-completions
   *
   *  @throws org.jline.reader.EndOfFileException This exception is thrown when the user types Ctrl-D.
   */
  def readLine(
    completer: Completer // provide auto-completions
  )(using Context): String = {
    import LineReader.Option.*
    import LineReader.*
    val userHome = System.getProperty("user.home")
    val lineReader = LineReaderBuilder
      .builder()
      .terminal(terminal)
      .history(history)
      .completer(completer)
      .highlighter(new Highlighter)
      .parser(new Parser)
      .variable(HISTORY_FILE, s"$userHome/.dotty_history") // Save history to file
      .variable(HISTORY_SIZE, 10000) // MP: adapted here; keep entire history file in memory - default is only 500... see DefaultHistory.DEFAULT_HISTORY_SIZE
      .variable(SECONDARY_PROMPT_PATTERN, "%M") // A short word explaining what is "missing",
                                                // this is supplied from the EOFError.getMissing() method
      .variable(LIST_MAX, 400)                  // Ask user when number of completions exceed this limit (default is 100).
      .variable(BLINK_MATCHING_PAREN, 0L)       // Don't blink the opening paren after typing a closing paren.
      .variable(WORDCHARS,
        LineReaderImpl.DEFAULT_WORDCHARS.filterNot("*?.[]~=/&;!#%^(){}<>".toSet)) // Finer grained word boundaries
      .option(INSERT_TAB, true)                 // At the beginning of the line, insert tab instead of completing.
      .option(AUTO_FRESH_LINE, true)            // if not at start of line before prompt, move to new line.
      .option(DISABLE_EVENT_EXPANSION, true)    // don't process escape sequences in input
      .build()

    lineReader.readLine(prompt)
  }

  def close(): Unit = terminal.close()

  /** Provide syntax highlighting */
  private class Highlighter(using Context) extends reader.Highlighter {
    def highlight(reader: LineReader, buffer: String): AttributedString = {
      val highlighted = replpp.SyntaxHighlighting.highlight(buffer)
      AttributedString.fromAnsi(highlighted)
    }
    def setErrorPattern(errorPattern: java.util.regex.Pattern): Unit = {}
    def setErrorIndex(errorIndex: Int): Unit = {}
  }

  /** Provide multi-line editing support */
  private class Parser(using Context) extends reader.Parser {

    /**
     * @param cursor     The cursor position within the line
     * @param line       The unparsed line
     * @param word       The current word being completed
     * @param wordCursor The cursor position within the current word
     */
    private class ParsedLine(
      val cursor: Int, val line: String, val word: String, val wordCursor: Int
    ) extends reader.ParsedLine {
      // Using dummy values, not sure what they are used for
      def wordIndex = -1
      def words = java.util.Collections.emptyList[String]
    }

    def parse(input: String, cursor: Int, context: ParseContext): reader.ParsedLine = {
      def parsedLine(word: String, wordCursor: Int) =
        new ParsedLine(cursor, input, word, wordCursor)
      // Used when no word is being completed
      def defaultParsedLine = parsedLine("", 0)

      def incomplete(): Nothing = throw new EOFError(
        // Using dummy values, not sure what they are used for
        /* line    = */ -1,
        /* column  = */ -1,
        /* message = */ "",
        /* missing = */ newLinePrompt)

      case class TokenData(token: Token, start: Int, end: Int)
      def currentToken: TokenData /* | Null */ = {
        val source = SourceFile.virtual("<completions>", input)
        val scanner = new Scanner(source)(using ctx.fresh.setReporter(Reporter.NoReporter))
        var lastBacktickErrorStart: Option[Int] = None

        while (scanner.token != EOF) {
          val start = scanner.offset
          val token = scanner.token
          scanner.nextToken()
          val end = scanner.lastOffset

          val isCurrentToken = cursor >= start && cursor <= end
          if (isCurrentToken)
            return TokenData(token, lastBacktickErrorStart.getOrElse(start), end)


          // we need to enclose the last backtick, which unclosed produces ERROR token
          if (token == ERROR && input(start) == '`') then
            lastBacktickErrorStart = Some(start)
          else
            lastBacktickErrorStart = None
        }
        null
      }

      def acceptLine = {
        val onLastLine = !input.substring(cursor).contains(System.lineSeparator)
        onLastLine && !ParseResult.isIncomplete(input)
      }

      context match {
        case ParseContext.ACCEPT_LINE if acceptLine =>
          // using dummy values, resulting parsed input is probably unused
          defaultParsedLine

        // In the situation where we have a partial command that we want to
        // complete we need to ensure that the :<partial-word> isn't split into
        // 2 tokens, but rather the entire thing is treated as the "word", in
        //   order to insure the : is replaced in the completion.
        case ParseContext.COMPLETE if
          DottyRandomStuff.ParseResult.commands.exists(command => command._1.startsWith(input)) =>
            parsedLine(input, cursor)

        case ParseContext.COMPLETE =>
          // Parse to find completions (typically after a Tab).
          def isCompletable(token: Token) = isIdentifier(token) || isKeyword(token)
          currentToken match {
            case TokenData(token, start, end) if isCompletable(token) =>
              val word = input.substring(start, end)
              val wordCursor = cursor - start
              parsedLine(word, wordCursor)
            case _ =>
              defaultParsedLine
          }

        case _ =>
          incomplete()
      }
    }
  }
}
