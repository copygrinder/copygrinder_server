import javax.xml.parsers.SAXParserFactory

import de.johoop.jacoco4sbt.JacocoPlugin.jacoco
import sbt._
import sbtassembly.AssemblyUtils
import sbtassembly.Plugin.AssemblyKeys._

import scala.xml.{Node, SAXParser, XML}

object CopygrinderBuild extends Build {

  val feedJacoco = taskKey[Unit]("Copies in the classes from the UberJar into the classes directory so Jacoco gets coverage of them.") := {

    val classDir = (Keys.classDirectory in Compile).value

    val uberJar = (outputPath in assembly).value

    AssemblyUtils.unzip(uberJar,classDir, Keys.streams.value.log)

  }


  val pruneJar = taskKey[Unit]("Removes any unused classes from the UberJar based on Jacoco coverage.") := {

    val jacocoDir = (jacoco.outputDirectory in jacoco.Config).value

    val classDir = (Keys.classDirectory in Compile).value

    deleteUnusedClasses(jacocoDir, classDir)
  }

  protected def deleteUnusedClasses(jacocoDir: File, classDir: File) {
    val jacocoXml = fetchJacocoXml(jacocoDir)

    val usedClasses = extractJacocoUsedClasses(jacocoXml.child)

    val allClassFiles = PathFinder(classDir).**(FileFilter.globFilter("*.class")).get

    allClassFiles.filterNot(file =>
      usedClasses.exists(usedClass =>
        file.getAbsolutePath.contains(usedClass)
      )
    ).foreach { file =>
      println("deleting " + file)
      file.delete()
    }

    deleteEmptyDirectories(classDir)
  }

  protected def deleteEmptyDirectories(classDir: File) = {
    val allDirectoriesStream = PathFinder(classDir).**(DirectoryFilter).get.toStream
    val sortedDirectories = allDirectoriesStream.sortWith(_.getAbsolutePath.length > _.getAbsolutePath.length)
    sortedDirectories.filter(_.list().isEmpty).foreach { dir =>
      println("deleting " + dir)
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

  protected def extractJacocoUsedClasses(nodes: Seq[Node]): Seq[String] = {
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

    usedClasses.flatMap(_.attribute("name")).map(_.text)
  }

  lazy val root: Project = Project(id = "copygrinder",
    base = file("."),
    settings = Seq(pruneJar, feedJacoco)
  )

}
