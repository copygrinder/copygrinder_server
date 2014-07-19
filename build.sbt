import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import org.sbtidea.SbtIdeaPlugin._

organization  := "org.copygrinder"

version       := "0.1"

scalaVersion  := "2.11.1"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/",
  "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

/* SCALA LIBS */
libraryDependencies ++= Seq(
  "io.spray"               %%   "spray-can"         % "1.3.1",
  "io.spray"               %%   "spray-routing"     % "1.3.1",
  "io.spray"               %%   "spray-testkit"     % "1.3.1",
  "org.json4s"             %%   "json4s-jackson"    % "3.2.10",
  "com.typesafe.akka"      %%   "akka-slf4j"        % "2.3.4"
)

/* JAVA LIBS */
libraryDependencies ++= Seq(
  "org.eclipse.jgit"    %   "org.eclipse.jgit"  % "3.4.1.201406201815-r",
  "commons-io"          %   "commons-io"        % "2.4",
  "ch.qos.logback"      %   "logback-classic"   % "1.1.2"
)

/* TEST LIBS */
libraryDependencies ++= Seq(
  "org.scalatest"       %%   "scalatest"                   % "2.2.0"   % "test",
  "org.scalamock"       %%  "scalamock-scalatest-support"  % "3.1.2"   % "test"
)

Revolver.settings.settings

instrumentSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

EclipseKeys.withSource := true

incOptions := incOptions.value.withNameHashing(true)

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"

fork := true

addCommandAlias("check", ";scalastyle;scoverage:test")
