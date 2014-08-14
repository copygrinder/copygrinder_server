import javax.xml.parsers.SAXParserFactory

import de.johoop.jacoco4sbt.JacocoPlugin.itJacoco
import sbt._
import sbtassembly.AssemblyUtils
import sbtassembly.Plugin.AssemblyKeys._

import scala.sys.process.ProcessLogger
import scala.xml.{Node, SAXParser, XML}

object CopygrinderBuild extends Build {

  val feedJacoco = taskKey[Unit]("Copies in the classes from the UberJar into the classes directory so Jacoco gets coverage of them.") := {

    val classDir = (Keys.classDirectory in Compile).value

    val uberJar = (outputPath in assembly).value

    AssemblyUtils.unzip(uberJar, classDir, Keys.streams.value.log)
  }

  val pruneClasses = taskKey[Unit]("Removes any unused classes from the UberJar based on Jacoco coverage.") := {

    implicit val logger = Keys.streams.value.log

    val jacocoDir = (itJacoco.outputDirectory in itJacoco.Config).value

    val classDir = (Keys.classDirectory in Compile).value

    deleteUnusedClasses(jacocoDir, classDir)
  }

  val createPruneJar = taskKey[Unit]("Creates a new smaller UberJar.") := {

    val classDir = (Keys.classDirectory in Compile).value

    val uberJar = (outputPath in assembly).value

    val allJarFiles = PathFinder(classDir).***.get

    val parentPath = classDir.getPath

    val fileAndPath = allJarFiles.zip(allJarFiles.map(_.getPath.replace(parentPath + "/", "")))

    IO.zip(fileAndPath, new File(uberJar.getPath.replace(".jar", ".min.jar")))
  }

  val trialRun = taskKey[Unit]("Tries running copygrinder and checks for errors.") := {
    val classDir = (Keys.classDirectory in Compile).value
    val buffer = new StringBuffer()

    val result = Process((Seq("java", "org.copygrinder.unpure.system.Boot")), classDir).lines_!
  }

  val keepClasses = Seq("")

  protected val keepClasses2 = Seq(
    "spray.routing.directives.ByNameDirective0",
    "spray.http.EntityTag",
    "spray.routing.directives.ChunkSizeMagnet",
    "spray.http.HttpCookie",
    "spray.routing.directives.DetachMagnet",
    "spray.routing.directives.LoggingMagnet",
    "spray.http.HttpEncoding",
    "spray.routing.directives.RefFactoryMagnet",
    "spray.routing.PathMatchers$Rest$",
    "spray.routing.directives.CompressResponseMagnet",
    "spray.routing.directives.EncodeResponseMagnet",
    "spray.routing.directives.NameReceptacle",
    "scala.Symbol",
    "spray.routing.PathMatchers$HexLongNumber$",
    "spray.routing.directives.RangeDirectives$WithRangeSupportMagnet",
    "spray.routing.directives.AuthMagnet",
    "scala.MatchError",
    "scala.collection.GenTraversable$",
    "scala.collection.GenIterable$",
    "scala.collection.GenSeq$",
    "scala.collection.SeqLike$$anon$1",
    "scala.collection.mutable.Traversable$",
    "scala.collection.mutable.Seq$",
    "scala.runtime.Nothing$",
    "scala.collection.mutable.Buffer$",
    "scala.collection.immutable.Traversable$",
    "scala.collection.immutable.Iterable$",
    "scala.NotImplementedError",
    "scala.UninitializedError",
    "scala.runtime.Null$",
    "spray.util.pimps.PimpedSeq",
    "spray.util.pimps.PimpedLinearSeq",
    "spray.util.pimps.PimpedIndexedSeq",
    "spray.http.IllegalUriException",
    "spray.http.ExceptionWithErrorInfo",
    "com.typesafe.config.impl.ConfigLong",
    "akka.ConfigurationException",
    "akka.actor.InvalidActorNameException",
    "akka.actor.InvalidMessageException",
    "akka.actor.Nobody$",
    "akka.actor.VirtualPathContainer",
    "akka.event.DummyClassForStringSources",
    "akka.actor.UntypedActorFactoryConsumer",
    "akka.actor.CreatorConsumer",
    "akka.actor.IllegalActorStateException",
    "akka.dispatch.sysmsg.Failed",
    "akka.dispatch.sysmsg.DeathWatchNotification",
    "akka.dispatch.sysmsg.Recreate",
    "akka.dispatch.sysmsg.Suspend",
    "akka.dispatch.sysmsg.Terminate",
    "akka.dispatch.sysmsg.Resume",
    "akka.pattern.AskTimeoutException",
    "akka.actor.DeathPactException",
    "ch.qos.logback.core.joran.spi.JoranException",
    "ch.qos.logback.core.status.WarnStatus",
    "akka.actor.Status$Success",
    "akka.actor.DeadLetter",
    "akka.actor.Terminated",
    "org.parboiled.errors.ParserRuntimeException",
    "org.parboiled.errors.ParsingException",
    "org.parboiled.scala.WithContextAction",
    "org.parboiled.errors.GrammarException",
    "org.parboiled.errors.ActionException",
    "org.parboiled.parserunners.RecoveringParseRunner$TimeoutException",
    "exception",
    "error",
    "spray.routing.PathMatchers",
    "akka.actor.dungeon.ChildrenContainer",
    "akka.actor.Status",
    "spray.can.client.HttpHostConnector",
    "org.parboiled.scala.Parser",
    "org.parboiled.support.MatcherPath",
    "org.parboiled.common.IntArrayStack",
    "org.parboiled.support.Position",
    "org.parboiled.matchers.VarFramingMatcher",
    "org.parboiled.matchers.MemoMismatchesMatcher",
    "akka.actor.dungeon.FaultHandling"
  )

  protected def deleteUnusedClasses(jacocoDir: File, classDir: File)(implicit logger: Logger) {
    val jacocoXml = fetchJacocoXml(jacocoDir)

    val (allJacocoClasses, usedClasses) = extractJacocoClasses(jacocoXml.child)

    val allClassFiles = PathFinder(classDir).**(FileFilter.globFilter("*.class")).get

    allClassFiles.filterNot(file => {
      val path = file.getAbsolutePath.toLowerCase
      val keepAndUsedClasses = usedClasses.++(keepClasses.map(_.replace(".", "/")))
      keepAndUsedClasses.exists(usedClass => {
        val usedClassWithExt = usedClass.toLowerCase
        val strippedUsedClass = usedClass.takeWhile(_ != '$')
        path.contains(usedClassWithExt) || path.contains(strippedUsedClass)
      })
    }).foreach { file =>
      val filePath = file.absolutePath.replace(".class", "").replace(classDir.absolutePath + "/", "")
      val fileKnownToJacoco = allJacocoClasses.contains(filePath)
      if (fileKnownToJacoco) {
        logger.debug("deleting " + file)
        file.delete()
      }
    }

    deleteEmptyDirectories(classDir)
  }

  protected def deleteEmptyDirectories(classDir: File)(implicit logger: Logger) = {
    val allDirectoriesStream = PathFinder(classDir).**(DirectoryFilter).get.toStream
    val sortedDirectories = allDirectoriesStream.sortWith(_.getAbsolutePath.length > _.getAbsolutePath.length)
    sortedDirectories.filter(_.list().isEmpty).foreach { dir =>
      logger.debug("deleting " + dir)
      dir.delete()
    }
  }

  protected def fetchJacocoXml(dir: File) = {

    val file = new File(dir, "/jacoco.xml")

    def parser: SAXParser = {
      val f = SAXParserFactory.newInstance()
      f.setNamespaceAware(false)
      f.setValidating(false)
      f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      f.newSAXParser()
    }

    val loader = XML.withSAXParser(parser)
    loader.loadFile(file)
  }

  protected def extractJacocoClasses(nodes: Seq[Node]): (Seq[String], Seq[String]) = {
    val classes = nodes
      .filter(_.label == "package")
      .flatMap(_.child)
      .filter(_.label == "class")

    val usedClasses = classes.filter(classNode => {
      val methods = classNode.toStream.flatMap(_.child).filter(_.label == "method")
      val counters = methods.flatMap(_.child).filter(_.label == "counter")
      val covereds = counters.flatMap(_.attribute("covered")).map(_.text)
      covereds.exists(_ != "0")
    })

    val allClassesText = classes.flatMap(_.attribute("name")).map(_.text)
    val usedClassesText = usedClasses.flatMap(_.attribute("name")).map(_.text)
    (allClassesText, usedClassesText)
  }

  lazy val root: Project = Project(id = "copygrinder",
    base = file("."),
    settings = Seq(feedJacoco, pruneClasses, createPruneJar, trialRun)
  ).configs(IntegrationTest).settings(Defaults.itSettings: _*)

}
