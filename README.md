## srp -> scala-repl-pp -> Scala REPL PlusPlus
<img src="https://github.com/user-attachments/assets/04bbb50b-dd9a-4aa4-b3dd-f9e21f5d6ead" width="300" />

When you read `srp` think "syrup" - full of goodness, glues things together :slightly_smiling_face:

`srp` enhances the stock Scala 3 REPL with features such as adding dependencies via maven coordinates and scripting. Plus: you can add it as a library dependency to your project, empowering it with a customizable REPL and scripting functionality.

## Use latest pre-built binary
```bash
curl -fL https://github.com/mpollmeier/scala-repl-pp/releases/latest/download/srp.zip
unzip srp.zip
srp/bin/srp
```

## Use as a library
```
libraryDependencies += "com.michaelpollmeier" % "scala-repl-pp" % "<version>" cross CrossVersion.full

# alternatively reference the scala version manually:
libraryDependencies += "com.michaelpollmeier" % "scala-repl-pp_3.6.4" % "<version>"
```
* `srp` is published with the full scala version suffix (e.g. `_3.6.4` instead of just `_3`) because the stock Scala REPL often has binary incompatible changes between minor version changes - different Scala patch versions typically work though, e.g. if your build uses Scala 3.6.3 you can use `scala-repl-pp_3.6.4`
* `srp` has only one direct dependency: the scala3-compiler[(*)](#fineprint)

As an example take a look at the demo project ["string calculator"](src/test/resources/demo-project) in this repository:
```bash
cd core/src/test/resources/demo-project
sbt stage
./stringcalc

Welcome to the magical world of string calculation!
Type `help` for help

stringcalc> add(One, Two)
val res0: stringcalc.Number = Number(3)

stringcalc> :exit  // or press Ctrl-D


./stringcalc --script plus.sc
executing plus.sc
Number(3)
```


## Table of contents
<!-- generated with:
markdown-toc --maxdepth 3 README.md|tail -n +5 
-->
- [Usage](#usage)
  * [run with defaults](#run-with-defaults)
  * [execute code at the start with `--runBefore`](#execute-code-at-the-start-with---runbefore)
  * [`--predef`: add source files to the classpath](#--predef-add-source-files-to-the-classpath)
  * [Operators: Redirect to file, pipe to external command](#operators-redirect-to-file-pipe-to-external-command)
  * [Add dependencies with maven coordinates](#add-dependencies-with-maven-coordinates)
  * [Importing additional script files interactively](#importing-additional-script-files-interactively)
  * [Adding classpath entries](#adding-classpath-entries)
- [REPL](#repl)
  * [Rendering of output](#rendering-of-output)
  * [Exiting the REPL](#exiting-the-repl)
  * [customize prompt, greeting and exit code](#customize-prompt-greeting-and-exit-code)
  * [Looking up the current terminal width](#looking-up-the-current-terminal-width)
- [Scripting](#scripting)
  * [Simple "Hello world" script](#simple-hello-world-script)
  * [Importing other files / scripts with `using file` directive](#importing-other-files--scripts-with-using-file-directive)
  * [Dependencies with `using dep` directive](#dependencies-with-using-dep-directive)
  * [@main entrypoints](#main-entrypoints)
  * [multiple @main entrypoints](#multiple-main-entrypoints)
  * [named parameters](#named-parameters)
- [Additional dependency resolvers and credentials](#additional-dependency-resolvers-and-credentials)
  * [Attach a debugger (remote jvm debug)](#attach-a-debugger-remote-jvm-debug)
- [Server mode](#server-mode)
- [Embed into your own project](#embed-into-your-own-project)
- [Verbose mode](#verbose-mode)
- [Inherited classpath](#inherited-classpath)
- [Parameters cheat sheet: the most important ones](#parameters-cheat-sheet-the-most-important-ones)
- [FAQ](#faq)
  * [Is this an extension of the stock REPL or a fork?](#is-this-an-extension-of-the-stock-repl-or-a-fork)
  * [Why do we ship a shaded copy of other libraries and not use dependencies?](#why-do-we-ship-a-shaded-copy-of-other-libraries-and-not-use-dependencies)
  * [Where's the cache located on disk?](#wheres-the-cache-located-on-disk)
  * [Why am I getting an AssertionError re `class module-info$` on first tab completion?](#why-am-i-getting-an-assertionerror-re-class-module-info-on-first-tab-completion)
- [Comparison / alternatives](#comparison--alternatives)
  * [Stock Scala REPL](#stock-scala-repl)
  * [scala-cli](#scala-cli)
  * [Ammonite](#ammonite)
- [Contribution guidelines](#contribution-guidelines)
  * [How can I build/stage a local version?](#how-can-i-buildstage-a-local-version)
  * [How can I get a new binary (bootstrapped) release?](#how-can-i-get-a-new-binary-bootstrapped-release)
  * [Adding support for a new Scala version](#adding-support-for-a-new-scala-version)
  * [Updating the shaded libraries](#updating-the-shaded-libraries)
- [Fineprint](#fineprint)



## Usage
The below features are all demonstrated using the REPL but also work when running scripts. 

### run with defaults
```bash
./srp
```

### execute code at the start with `--runBefore`
```
./srp --runBefore "import Byte.MaxValue"

scala> MaxValue
val res0: Int = 127
```

You can specify this parameter multiple times, the given statements will be executed in the given order.

If you want to execute some code _every single time_ you start a session, just write it to `~/.scala-repl-pp.sc`
```bash
echo 'import Short.MaxValue' > ~/.scala-repl-pp.sc

./srp

scala> MaxValue
val res0: Int = 32767
```

If the code you want to execute on startup is in a file, you can use your shell tooling:
```bash
echo 'import Int.MaxValue' > /tmp/runBeforeFile.sc
./srp --runBefore "$(cat /tmp/runBeforeFile.sc)"

scala> MaxValue
val res0: Int = 2147483647
```

### `--predef`: add source files to the classpath
Additional source files that are compiled added to the classpath, but unlike `runBefore` not executed straight away can be provided via `--predef`. 
```
echo 'def foo = 42' > foo.sc

./srp --predef foo.sc
scala> foo
val res0: Int = 42
```

You can specify this parameter multiple times (`--predef one.sc --predef two.sc`).

Why not use `runBefore` instead? For simple examples like the one above, you can do so. If it gets more complicated and you have multiple files referencing each others, `predef` allows you to treat it as one compilation unit, which isn't possible with `runBefore`. And as you add more code it's get's easier to manage in files rather than command line arguments. 

Note that predef files may not contain toplevel statements like `println("foo")` - instead, these either belong into your main script (if you're executing one) and/or can be passed to the repl via `runBefore`.

### Operators: Redirect to file, pipe to external command
Inspired by unix shell redirection and pipe operators (`>`, `>>` and `|`) you can redirect output into files with `#>` (overrides existing file) and `#>>` (create or append to file), and use `#|` to pipe the output to a command, such as `less`:
```scala
./srp

scala> "hey there" #>  "out.txt"
scala> "hey again" #>> "out.txt"
scala> Seq("a", "b", "c") #>> "out.txt"

// pipe results to external command
scala> Seq("a", "b", "c") #| "cat"
val res0: String = """a
b
c"""

// pipe results to external command with arguments
scala> Seq("foo", "bar", "foobar") #| ("grep", "foo")
val res1: String = """foo
foobar"""

// pipe results to external command and let it inherit stdin/stdout
scala> Seq("a", "b", "c") #|^ "less"

// pipe results to external command with arguments and let it inherit stdin/stdout
scala> Seq("a", "b", "c") #|^ ("less", "-N")
```

All operators use the same pretty-printing that's used within the REPL, i.e. you get structured rendering including product labels etc. 
```scala
scala> case class PrettyPrintable(s: String, i: Int)
scala> PrettyPrintable("two", 2) #> "out.txt"
// out.txt now contains `PrettyPrintable(s = "two", i = 2)` - in pretty colors
```

The operators have a special handling for two common use cases that are applied at the root level of the object you hand them: list- or iterator-type objects are unwrapped and their elements are rendered in separate lines, and Strings are rendered without the surrounding `""`. Examples:
```scala
scala> "a string" #> "out.txt"
// rendered as `a string` without quotes

scala> Seq("one", "two") #> "out.txt"
// rendered as two lines without quotes:
// one
// two

scala> Seq("one", Seq("two"), Seq("three", 4), 5) #> "out.txt"
// top-level list-types are unwrapped
// resulting top-level strings are rendered without quotes:
// one
// List("two")
// List("three", 4)
// 5
```

All operators are prefixed with `#` in order to avoid naming clashes with more basic operators like `>` for greater-than-comparisons. This naming convention is inspired by scala.sys.process.

### Add dependencies with maven coordinates
Note: the dependencies must be known at startup time, either via `--dep` parameter:
```
./srp --dep com.michaelpollmeier:versionsort:1.0.7
scala> versionsort.VersionHelper.compare("1.0", "0.9")
val res0: Int = 1
```
To add multiple dependencies, you can specify this parameter multiple times.

Alternatively, use the `//> using dep` directive in predef code or predef files:
```
echo '//> using dep com.michaelpollmeier:versionsort:1.0.7' > predef.sc

./srp --predef predef.sc

scala> versionsort.VersionHelper.compare("1.0", "0.9")
val res0: Int = 1
```

For Scala dependencies use `::`:
```
./srp --dep com.michaelpollmeier::colordiff:0.36

colordiff.ColorDiff(List("a", "b"), List("a", "bb"))
// color coded diff
```

Note: if your dependencies are not hosted on maven central, you can [specify additional resolvers](#additional-dependency-resolvers-and-credentials) - including those that require authentication)

Implementation note: `srp` uses [coursier](https://get-coursier.io/) to fetch the dependencies. We invoke it in a subprocess via the coursier java launcher, in order to give our users maximum control over the classpath.

### Importing additional script files interactively
```
echo 'val bar = "foo"' > myScript.sc

./srp

//> using file myScript.sc
println(bar) //foo
```

You can specify the filename with relative or absolute paths:
```java
//> using file scripts/myScript.sc
//> using file ../myScript.sc
//> using file /path/to/myScript.sc
```

### Adding classpath entries
Prerequisite: create some .class files:
```bash
mkdir foo
cd foo
echo 'class Foo { def foo = 42 } ' > Foo.scala
scalac Foo.scala
cd ..
```

Now let's start the repl with those in the classpath:
```bash
./srp --classpathEntry foo

scala> new Foo().foo
val res0: Int = 42
```

For scripts you can use the `//> using classpath` directive:
```bash
echo '//> using classpath foo
println(new Foo().foo)' > myScript.sc

./srp --script myScript.sc
```

## REPL

### Rendering of output

Unlike the stock Scala REPL, `srp` does _not_ truncate the output by default. You can optionally specify the maxHeight parameter though:
```
./srp --maxHeight 5
scala> (1 to 100000).toSeq
val res0: scala.collection.immutable.Range.Inclusive = Range(
  1,
  2,
  3,
...
```

### Exiting the REPL
Famously one of the most popular question on stackoverflow is about how to exit `vim` - fortunately you can apply the answer as-is to exit `srp` :slightly_smiling_face:
```
// all of the following exit the REPL
:exit
:quit
:q
```

When the REPL is waiting for input we capture `Ctrl-c` and don't exit. If there's currently a long-running execution that you really *might* want to cancel you can press `Ctrl-c` again immediately which will kill the entire repl:
```
scala> Thread.sleep(50000)
// press Ctrl-c
Captured interrupt signal `INT` - if you want to kill the REPL, press Ctrl-c again within three seconds

// press Ctrl-c again will exit the repl
$
```
Context: we'd prefer to cancel the long-running operation, but that's not so easy on the JVM.

### customize prompt, greeting and exit code
```
./srp --prompt myprompt --greeting 'hey there!' --runAfter 'println("see ya!")'

hey there!
myprompt> :exit
see ya!
```

### Looking up the current terminal width
In case you want to adjust your output rendering to the available terminal size, you can look it up:

```
scala> replpp.util.terminalWidth
val res0: util.Try[Int] = Success(value = 202)
```

## Scripting

See [ScriptRunnerTest](core/src/test/scala/replpp/scripting/ScriptRunnerTest.scala) for a more complete and in-depth overview.

### Simple "Hello world" script
```bash
echo 'println("Hello!")' > test-simple.sc

./srp --script test-simple.sc
cat out.txt # prints 'i was here'
```

### Importing other files / scripts with `using file` directive
```bash
echo 'val foo = 42' > foo.sc

echo '//> using file foo.sc
println(foo)' > test.sc

./srp --script test.sc
```

### Dependencies with `using dep` directive
Dependencies can be added via `//> using dep` syntax (like in scala-cli).

```bash
echo '//> using dep com.michaelpollmeier:versionsort:1.0.7

val compareResult = versionsort.VersionHelper.compare("1.0", "0.9")
assert(compareResult == 1,
       s"result of comparison should be `1`, but was `$compareResult`")
' > test-dependencies.sc

./srp --script test-dependencies.sc
```

Note: this also works with `using` directives in your predef code - for script and REPL mode.

### @main entrypoints
```bash
echo '@main def main() = println("Hello, world!")' > test-main.sc

./srp --script test-main.sc
```

### multiple @main entrypoints
```bash
echo '
@main def foo() = println("foo!")
@main def bar() = println("bar!")
' > test-main-multiple.sc

./srp --script test-main-multiple.sc --command foo
```

### named parameters
```bash
echo '
@main def main(first: String, last: String) = {
  println(s"Hello, $first $last!")
} ' > test-main-withargs.sc

./srp --script test-main-withargs.sc --param first=Michael --param last=Pollmeier
```
If your parameter value contains whitespace, just wrap it quotes so that your shell doesn't split it up, e.g. `--param "text=value with whitespace"`

On windows the parameters need to be triple-quoted in any case:
`srp.bat --script test-main-withargs.sc --param """first=Michael""" --param """last=Pollmeier"""`

## Additional dependency resolvers and credentials
Via `--repo` parameter on startup:
```bash
./srp --repo "https://repo.gradle.org/gradle/libs-releases" --dep org.gradle:gradle-tooling-api:7.6.1
scala> org.gradle.tooling.GradleConnector.newConnector()
```
To add multiple dependency resolvers, you can specify this parameter multiple times.

Or via `//> using resolver` directive as part of your script or predef code:

```bash
echo '
//> using resolver https://repo.gradle.org/gradle/libs-releases
//> using dep org.gradle:gradle-tooling-api:7.6.1
println(org.gradle.tooling.GradleConnector.newConnector())
' > script-with-resolver.sc

./srp --script script-with-resolver.sc
```

If one or multiple of your resolvers require authentication, you can configure your username/passwords in a [`credentials.properties` file](https://get-coursier.io/docs/other-credentials#property-file):
```
mycorp.realm=Artifactory Realm
mycorp.host=shiftleft.jfrog.io
mycorp.username=michael
mycorp.password=secret

otherone.username=j
otherone.password=imj
otherone.host=nexus.other.com
```
The prefix is arbitrary and is only used to specify several credentials in a single file. `srp` uses [coursier](https://get-coursier.io) to resolve dependencies. 

### Attach a debugger (remote jvm debug)
For the REPL itself:
```
export JAVA_OPTS='-Xdebug -Xrunjdwp:transport=dt_socket,address=5005,server=y,suspend=y'
./srp
unset JAVA_OPTS
```
Then attach your favorite IDE / debugger on port 5005. 

If you want to debug a script, it's slightly different. Scripts are executed in a separate subprocess - just specify the following parameter (and make sure `JAVA_OPTS` isn't also set).
```
./srp --script myScript.sc --remoteJvmDebug
```

## Server mode
Note: `srp-server` isn't currently available as a bootstrapped binary, so you have to [stage it locally](#how-can-i-buildstage-a-local-version) first using `sbt stage`.
```bash
./srp-server

curl http://localhost:8080/query-sync -X POST -d '{"query": "val foo = 42"}'
# {"success":true,"stdout":"val foo: Int = 42\n",...}

curl http://localhost:8080/query-sync -X POST -d '{"query": "val bar = foo + 1"}'
# {"success":true,"stdout":"val bar: Int = 43\n",...}

curl http://localhost:8080/query-sync -X POST -d '{"query":"println(\"OMG remote code execution!!1!\")"}'
# {"success":true,"stdout":"",...}%
```

For a nice user experience enable colors and create small wrapper function to interact with the server:
```bash
./srp-server --colors

function srp-remote() {
  QUERY="{\"query\": \"$@\"}"
  curl --silent http://localhost:8080/query-sync -X POST -d $QUERY | jq --raw-output .stdout
}

srp-remote 'val foo = 42'
> val foo: Int = 42
```
<img src="https://github.com/user-attachments/assets/f97a3701-cac5-4f56-9b3f-a00405a3fb1f" width="500" />

The same for windows and powershell:
```
./srp-server.bat

Invoke-WebRequest -Method 'Post' -Uri http://localhost:8080/query-sync -ContentType "application/json" -Body '{"query": "val foo = 42"}'
# Content           : {"success":true,"stdout":"val foo: Int = 42\r\n","uuid":"02f843ba-671d-4fb5-b345-91c1dcf5786d"}
Invoke-WebRequest -Method 'Post' -Uri http://localhost:8080/query-sync -ContentType "application/json" -Body '{"query": "foo + 1"}'
# Content           : {"success":true,"stdout":"val res0: Int = 43\r\n","uuid":"dc49df42-a390-4177-98d0-ac87a277c7d5"}
```

Predef code and runBefore work as well:
```
echo val foo = 99 > foo.sc
./srp-server --predef foo.sc --runBefore 'import Short.MaxValue'

curl -XPOST http://localhost:8080/query-sync -d '{"query":"val baz = foo + MaxValue"}'
# {"success":true,"stdout":"val baz: Int = 32866\n",...}
```

Adding dependencies:
```
echo '//> using dep com.michaelpollmeier:versionsort:1.0.7' > foo.sc
./srp-server --predef foo.sc

curl http://localhost:8080/query-sync -X POST -d '{"query": "versionsort.VersionHelper.compare(\"1.0\", \"0.9\")"}'
# {"success":true,"stdout":"val res0: Int = 1\n",...}%
```

`srp-server` can be used in an asynchronous mode:
```
./srp-server

curl http://localhost:8080/query -X POST -d '{"query": "val baz = 93"}'
# {"success":true,"uuid":"e2640fcb-3193-4386-8e05-914b639c3184"}%

curl http://localhost:8080/result/e2640fcb-3193-4386-8e05-914b639c3184
{"success":true,"uuid":"e2640fcb-3193-4386-8e05-914b639c3184","stdout":"val baz: Int = 93\n"}%
```

There's even a websocket channel that allows you to get notified when the query has finished. For more details and other use cases check out [ReplServerTests.scala](server/src/test/scala/replpp/server/ReplServerTests.scala)

Server-specific configuration options as per `srp --help`:
```
--server-host <value>    Hostname on which to expose the REPL server
--server-port <value>    Port on which to expose the REPL server
--server-auth-username <value> Basic auth username for the REPL server
--server-auth-password <value> Basic auth password for the REPL server
```

## Verbose mode
If verbose mode is enabled, you'll get additional information about classpaths and complete scripts etc. 
To enable it, you can either pass `--verbose` or set the environment variable `SCALA_REPL_PP_VERBOSE=true`.

## Inherited classpath
`srp` comes with it's own classpath dependencies, and depending on how you invoke it there are different requirements for controlling the inherited classpath. E.g. if you add `srp` as a dependency to your project and want to simply use all dependencies from that same project, you can configure `--cpinherit` (or programatically `replpp.Config.classpathConfig.inheritClasspath`). You can also include or exclude dependencies via regex expressions.

## Parameters cheat sheet: the most important ones
Here's only the most important ones - run `srp --help` for all parameters.

| parameter     | short         | description                           
| ------------- | ------------- | --------------------------------------
| `--predef`    | `-p`          | Import additional files
| `--dep`       | `-d`          | Add dependencies via maven coordinates
| `--repo`      | `-r`          | Add repositories to resolve dependencies
| `--script`    |               | Execute given script
| `--param`     |               | key/value pair for main function in script
| `--verbose`   | `-v`          | Verbose mode

## FAQ

### Is this an extension of the stock REPL or a fork?
Technically it is a fork, i.e. we copied parts of the ReplDriver to make some adjustments. 
However, semantically, `srp` can be considered an extension of the stock repl. We don't want to create and maintain a competing REPL implementation, 
instead the idea is to provide a space for exploring new ideas and bringing them back into the dotty codebase. 
[When we forked](https://github.com/mpollmeier/scala-repl-pp/commit/eb2bf9a3bed681bffa945f657ada14781c6a7a14) the stock ReplDriver, we made sure to separate the commits into bitesized chunks so we can easily rebase. The changes are clearly marked, and whenever there's a new dotty version we're bringing in the relevant changes here (`git diff 3.3.0-RC5..3.3.0-RC6 compiler/src/dotty/tools/repl/`).

### Why do we ship a shaded copy of other libraries and not use dependencies?
`srp` includes some small libraries (e.g. most of the com-haoyili universe) that have been copied as-is, but then moved into the `replpp.shaded` namespace. We didn't include them as regular dependencies, because repl users may want to use a different version of them, which may be incompatible with the version the repl uses. Thankfully their license is very permissive - a big thanks to the original authors! The instructions of how to (re-) import then and which versions were used are available in [import-instructions.md](shaded-libs/import-instructions.md).

### Where's the cache located on disk?
The cache? The caches you mean! :)
There's `~/.cache/scala-repl-pp` for the repl itself. Since we use coursier (via a subprocess) there's also `~/.cache/coursier`. 

### Why am I getting an AssertionError re `class module-info$` on first tab completion?
```
exception caught when loading module class module-info$: java.lang.AssertionError: assertion failed: attempt to parse java.lang.Object from classfile
```
There's a [Scala 3 compiler bug](https://github.com/scala/scala3/issues/20421) that triggers and prints this exception if one of your dependencies ships a `module-info.class`. Until that's fixed you can use this hacky workaround in your sbt build:
```
lazy val removeModuleInfoFromJars = taskKey[Unit]("remove module-info.class from dependency jars - a hacky workaround for a scala3 compiler bug https://github.com/scala/scala3/issues/20421")
removeModuleInfoFromJars := {
  import java.nio.file.{Files, FileSystems}
  val logger = streams.value.log
  val libDir = (Universal/stagingDirectory).value / "lib"

  // remove all `/module-info.class` from all jars
  Files.walk(libDir.toPath)
    .filter(_.toString.endsWith(".jar"))
    .forEach { jar =>
      val zipFs = FileSystems.newFileSystem(jar)
      zipFs.getRootDirectories.forEach { zipRootDir =>
        Files.list(zipRootDir).filter(_.toString == "/module-info.class").forEach { moduleInfoClass =>
          logger.info(s"workaround for scala completion bug: deleting $moduleInfoClass from $jar")
          Files.delete(moduleInfoClass)
        }
      }
      zipFs.close()
    }
}
```

If you use [sbt-native-packager](https://sbt-native-packager.readthedocs.io/en/latest/) to package your application, you can automatically invoke the task, e.g. like so:
```
removeModuleInfoFromJars := removeModuleInfoFromJars.triggeredBy(Universal/stage).value
```


## Comparison / alternatives
Many features of `srp` were shaped by ammonite and scala-cli - thank you! I would have preferred to use those projects instead of creating `srp`, but they lacked certain features that I needed - most importantly I needed the relative maturity of the stock Scala REPL with the ability to include it as a library. Here's a rough overview of the differences between `srp` and other options:

### Stock Scala REPL
`srp` allows you to:
* use it as a library with minimal dependencies in your own build
* add runtime dependencies on startup with maven coordinates - it automatically handles all downstream dependencies via [coursier](https://get-coursier.io/)
* use `#>`, `#>>` and `#|` operators to redirect output to file and pipe to external command
* customize the greeting, prompt and shutdown code
* multiple @main with named arguments (regular Scala REPL only allows an argument list)
* import additional files with directives and parameters
* run code on startup and shutdown
* server mode: REPL runs embedded
* structured rendering including product labels and type information:<br/>
Scala-REPL-PP:<br/>
<img src="https://github.com/mpollmeier/scala-repl-pp/assets/506752/2e24831e-3c0d-4b07-8453-1fa267a6a6bf" width="700px"/>
<br/>
Stock Scala REPL:<br/>
<img src="https://github.com/mpollmeier/scala-repl-pp/assets/506752/77d006d1-35ef-426f-a3b8-1311a36ffed5" width="700px"/>

### [scala-cli](https://scala-cli.virtuslab.org/)
* `srp` allows you to use it as a library with minimal dependencies in your own build
* scala-cli wraps and invokes the regular Scala REPL (by default; or optionally Ammonite). It doesn't modify/fix the REPL itself, i.e. most differences between `srp` and the stock scala repl from above apply, with the exception of e.g. dependencies: scala-cli does let you add them on startup as well.
* `srp` has a 66.6% shorter name :slightly_smiling_face:

### [Ammonite](http://ammonite.io)
* `srp` allows you to use it as a library with minimal dependencies in your own build
* Ammonite's Scala 3 support is far from complete - e.g. autocompletion for extension methods has [many shortcomings](https://github.com/com-lihaoyi/Ammonite/issues/1297). In comparison: `srp` uses the regular Scala3/dotty ReplDriver. 
* Ammonite has some Scala2 dependencies intermixed, leading to downstream build problems like [this](https://github.com/com-lihaoyi/Ammonite/issues/1241). It's no longer easy to embed Ammonite into your own build.
* Note: Ammonite allows to add dependencies dynamically even in the middle of the REPL session - that's not supported by `srp` currently. You need to know which dependencies you want on startup. 



## Contribution guidelines

### How can I build/stage a local version?
```bash
sbt stage
./srp
```

### How can I get a new binary (bootstrapped) release?
While maven central jar releases are created for each commit on master (a new version tag is assigned automatically), the binary (bootstrapped) releases that end up in https://github.com/mpollmeier/scala-repl-pp/releases/latest are being triggered manually. Contributors can run the [bootstrap action](https://github.com/mpollmeier/scala-repl-pp/actions/workflows/bootstrap.yml).

### Adding support for a new Scala version
First, get relevant diff from dotty repo:
```bash
cd /path/to/dotty
git fetch

OLD=3.5.2-RC2 # set to version that was used before you bumped it
NEW=3.6.4     # set to version that you bumped it to
git diff $OLD..$NEW compiler/src/dotty/tools/repl
```
Check if any of those changes need to be reapplied to this repo - some files have been copied and slightly adjusted, the majority of functionality is reused. 
If there's any binary incompatible changes (which is typically the case between minor versions), you need to add new projects for `core` and `server` in [build.sbt](build.sbt), add new `core/src/main/scala-3.x.y` directories etc.

### Updating the shaded libraries
See [import-instructions.md](shaded-libs/import-instructions.md).

## Fineprint
(*) To keep our codebase concise we do use libraries, most importantly the [com.lihaoyi](https://github.com/com-lihaoyi/) stack. We want to ensure that users can freely use their own dependencies without clashing with the `srp` classpath though, so we [copied them into our build](shaded-libs/src/main/scala/replpp/shaded) and [changed the namespace](shaded-libs/import-instructions) to `replpp.shaded`. Many thanks to the original authors, also for choosing permissive licenses. 
  
  
