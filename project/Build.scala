import de.johoop.jacoco4sbt.{HTMLReport, XMLReport}
import sbt.Keys._
import sbt._
import de.johoop.jacoco4sbt.JacocoPlugin._
import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import org.sbtidea.SbtIdeaPlugin._
import scoverage.ScoverageSbtPlugin._
import sbtassembly.Plugin._

object CopygrinderBuild extends Build {

  lazy val root: Project = Project(
    id = "copygrinder",
    base = file(".")
  ).settings(

     organization := "org.copygrinder",

     version := "0.1",

     scalaVersion := "2.11.2",

     scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),

     resolvers ++= Seq(
       "spray repo" at "http://repo.spray.io/",
       "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
       "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
       "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
     ),

     /* SCALA LIBS */
     libraryDependencies ++= Seq(
       "io.spray" %% "spray-caching" % "1.3.1",
       "io.spray" %% "spray-can" % "1.3.1",
       "io.spray" %% "spray-routing" % "1.3.1",
       "com.typesafe.akka" %% "akka-slf4j" % "2.3.5",
       "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
       "com.typesafe.play" %% "play-json" % "2.3.5"
     ),

     /* JAVA LIBS */
     libraryDependencies ++= Seq(
       "org.eclipse.jgit" % "org.eclipse.jgit" % "3.4.1.201406201815-r",
       "commons-io" % "commons-io" % "2.4",
       "ch.qos.logback" % "logback-classic" % "1.1.2",
       "org.apache.lucene" % "lucene-analyzers-common" % "4.10.0"
     ),

     /* TEST LIBS */
     libraryDependencies ++= Seq(
       "org.scalatest" %% "scalatest" % "2.2.2" % "test, it",
       "org.scalamock" %% "scalamock-scalatest-support" % "3.1.2" % "test, it",
       "io.spray" %% "spray-testkit" % "1.3.1" % "test, it",
       "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % "test, it"
     ),

     ScoverageKeys.highlighting := true,
     unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil,
     unmanagedSourceDirectories in IntegrationTest := (scalaSource in IntegrationTest).value :: Nil,
     EclipseKeys.withSource := true,
     incOptions := incOptions.value.withNameHashing(true),
     fork := true,
     test in AssemblyKeys.assembly := {},
     itJacoco.reportFormats in itJacoco.Config := Seq(XMLReport(encoding = "utf-8"), HTMLReport()),
     ideaExcludeFolders := ".idea" :: ".idea_modules" :: "target" :: "data" :: "gatling" :: Nil,
     testOptions in IntegrationTest += Tests.Argument("-oS")
   ).settings(
     Revolver.settings: _*
   ).settings(
     instrumentSettings: _*
   ).settings(
     org.scalastyle.sbt.ScalastylePlugin.Settings: _*
   ).settings(
     jacoco.settings: _*
   ).settings(
     itJacoco.settings: _*
   ).settings(
     net.virtualvoid.sbt.graph.Plugin.graphSettings: _*
   ).settings(
     assemblySettings: _*
   ).settings(
     addCommandAlias("check", ";scalastyle;scoverage:test"): _*
   ).settings(
     Defaults.itSettings: _*
   ).configs(IntegrationTest)
}
