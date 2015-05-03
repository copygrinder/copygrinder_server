import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import sbtide.Keys._

object CopygrinderBuild extends Build {

  lazy val root: Project = Project(
    id = "copygrinder",
    base = file(".")
  ).settings(

     organization := "org.copygrinder",

     version := "0.1",

     scalaVersion := "2.11.6",

     scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8", "-feature", "-language:postfixOps"),

     resolvers ++= Seq(
       "spray repo" at "http://repo.spray.io/",
       "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/",
       "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
     ),

     /* SCALA LIBS */
     libraryDependencies ++= Seq(
       "io.spray" %% "spray-caching" % "1.3.2",
       "io.spray" %% "spray-can" % "1.3.2",
       "io.spray" %% "spray-routing-shapeless2" % "1.3.2",
       "com.typesafe.akka" %% "akka-slf4j" % "2.3.9",
       "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
       "com.typesafe.play" %% "play-json" % "2.3.8",
       "com.github.julien-truffaut" %% "monocle-core" % "1.0.1",
       "com.github.julien-truffaut" %% "monocle-generic" % "1.0.1",
       "com.github.julien-truffaut" %% "monocle-macro" % "1.0.1"
     ),

     /* JAVA LIBS */
     libraryDependencies ++= Seq(
       "commons-io" % "commons-io" % "2.4",
       "ch.qos.logback" % "logback-classic" % "1.1.2",
       "com.lambdaworks" % "scrypt" % "1.4.0",
       "org.apache.camel" % "camel-core" % "2.14.1",
       "org.mapdb" % "mapdb" % "1.0.7",
       "net.jpountz.lz4" % "lz4" % "1.3"
     ),

     /* TEST LIBS */
     libraryDependencies ++= Seq(
       "org.scalatest" %% "scalatest" % "2.2.4" % "test, it",
       "org.scalamock" %% "scalamock-scalatest-support" % "3.2.1" % "test, it",
       "io.spray" %% "spray-testkit" % "1.3.2" % "test, it",
       "net.databinder.dispatch" %% "dispatch-core" % "0.11.2" % "test, it"
     ),

     unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil,
     unmanagedSourceDirectories in IntegrationTest := (scalaSource in IntegrationTest).value :: Nil,
     EclipseKeys.withSource := true,
     incOptions := incOptions.value.withNameHashing(true),
     fork := true,
     ideExcludedDirectories := List(".idea", ".idea_modules", "target", "data", "gatling", "admin-src/.tmp",
       "admin-src/dist", "admin-src/node_modules", "admin-src/target", "admin-src/app/components",
       "src/main/resources/admin").map(new File(_)),
     testOptions in IntegrationTest += Tests.Argument("-oS")
   ).settings(
     Revolver.settings: _*
   ).settings(
     net.virtualvoid.sbt.graph.Plugin.graphSettings: _*
   ).settings(
     addCommandAlias("check", ";scalastyle;coverage;test;it:test;reload"): _*
   ).settings(
     addCommandAlias("buildAdminNpm", """;eval Process("npm install", new java.io.File("admin-src")) ! """): _*
   ).settings(
     addCommandAlias("buildAdminGrunt", """;eval Process("grunt toServer", new java.io.File("admin-src")) ! """): _*
   ).settings(
     addCommandAlias("megaBuild", """;check;buildAdminNpm;buildAdminGrunt;assembly """): _*
   ).settings(
     Defaults.itSettings: _*
   ).configs(IntegrationTest)
}
