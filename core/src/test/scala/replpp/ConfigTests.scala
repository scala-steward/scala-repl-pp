package replpp

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import replpp.Colors

import java.nio.file.Paths

class ConfigTests extends AnyWordSpec with Matchers {
  val apacheRepo = "https://repository.apache.org/content/groups/public"
  val sonatypeRepo = "https://oss.sonatype.org/content/repositories/public"

  "color modes" in {
    Config().colors shouldBe Colors.Default
    Config(nocolors = false).colors shouldBe Colors.Default
    Config(nocolors = true).colors shouldBe Colors.BlackWhite
  }

  "asJavaArgs (inverse of Config.parse)" in {
    val config = Config(
      predefFiles = List(Paths.get("/some/path/predefFile1"), Paths.get("/some/path/predefFile2")),
      runBefore = List("val foo = 42", "println(foo)"),
      runAfter = List("""val msg = "goodbye!"""", "println(msg)"),
      nocolors = true,
      verbose = true,
      classpathConfig = Config.ForClasspath(
        additionalClasspathEntries = Seq("cp1", "../path/to/cp2"),
        inheritClasspath = true,
        inheritClasspathIncludes = Config.ForClasspath.DefaultInheritClasspathIncludes ++ Seq(".*include1", "include2.*"),
        inheritClasspathExcludes = Seq(".*exclude1", "exclude2.*"),
        dependencies = Seq("com.michaelpollmeier:versionsort:1.0.7", "foo:bar:1.2.3"),
        resolvers = Seq(apacheRepo, sonatypeRepo),
      ),
      maxHeight = Some(10000),
      scriptFile = Some(Paths.get("/some/script.sc")),
      command = Some("someCommand"),
      params = Map("param1" -> "value1", "param2" -> "222", "someEquation" -> "40 + 2 = 42"),
    )

    val javaArgs = config.asJavaArgs
    javaArgs shouldBe Seq(
      "--predef", Paths.get("/some/path/predefFile1").toString,
      "--predef", Paths.get("/some/path/predefFile2").toString,
      "--runBefore", "val foo = 42",
      "--runBefore", "println(foo)",
      "--runAfter", """val msg = "goodbye!"""",
      "--runAfter", "println(msg)",
      "--nocolors",
      "--verbose",
      "--classpathEntry", "cp1",
      "--classpathEntry", "../path/to/cp2",
      "--cpinherit",
      "--cpinclude", ".*include1",
      "--cpinclude", "include2.*",
      "--cpexclude", ".*exclude1",
      "--cpexclude", "exclude2.*",
      "--dep", "com.michaelpollmeier:versionsort:1.0.7",
      "--dep", "foo:bar:1.2.3",
      "--repo", apacheRepo,
      "--repo", sonatypeRepo,
      "--maxHeight", "10000",
      "--script", Paths.get("/some/script.sc").toString,
      "--command", "someCommand",
      "--param", "param1=value1",
      "--param", "param2=222",
      "--param", "someEquation=40 + 2 = 42",
    )

    // round trip
    Config.parse(javaArgs.toArray) shouldBe config
  }

}
