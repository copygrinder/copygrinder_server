import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import org.sbtidea.SbtIdeaPlugin._

organization  := "org.copygrinder"

version       := "0.1"

scalaVersion  := "2.11.0"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/",
  "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

/* SCALA LIBS */
libraryDependencies ++= Seq(
  "io.spray"               %%   "spray-can"         % "1.3.1-20140423",
  "io.spray"               %%   "spray-routing"     % "1.3.1-20140423",
  "io.spray"               %%   "spray-testkit"     % "1.3.1-20140423",
  "org.scala-lang.modules" %%   "scala-xml"         % "1.0.1"
)

/* JAVA LIBS */
libraryDependencies ++= Seq(
  "org.eclipse.jgit"    %   "org.eclipse.jgit"  % "3.1.0.201310021548-r",
  "commons-io"          %   "commons-io"        % "2.4"
)

/* TEST LIBS */
libraryDependencies ++= Seq(
  "org.scalatest"       %%   "scalatest"                   % "2.1.5"   % "test",
  "org.scalamock"       %%  "scalamock-scalatest-support"  % "3.1.1"   % "test"
)

Revolver.settings.settings

ScoverageSbtPlugin.instrumentSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

EclipseKeys.withSource := true

incOptions := incOptions.value.withNameHashing(true)

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"

fork := true
