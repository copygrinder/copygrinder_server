import spray.revolver.RevolverPlugin.Revolver
import com.typesafe.sbteclipse.core.EclipsePlugin.EclipseKeys
import org.sbtidea.SbtIdeaPlugin._

organization  := "org.copygrinder"

version       := "0.1"

scalaVersion  := "2.10.4"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io/",
  "JGit repo" at "https://repo.eclipse.org/content/groups/releases/",
  "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"
)

/* SCALA LIBS */
libraryDependencies ++= Seq(
  "io.spray"            %   "spray-can"         % "1.3.1",
  "io.spray"            %   "spray-routing"     % "1.3.1",
  "io.spray"            %   "spray-testkit"     % "1.3.1",
  "io.spray"            %%  "spray-json"        % "1.2.6",
  "com.typesafe.akka"   %%  "akka-actor"        % "2.3.1",
  "com.typesafe.akka"   %%  "akka-testkit"      % "2.3.2"
)

/* JAVA LIBS */
libraryDependencies ++= Seq(
  "org.eclipse.jgit"    %   "org.eclipse.jgit"  % "3.1.0.201310021548-r",
  "commons-io"          %   "commons-io"        % "2.4"
)

/* TEST LIBS */
libraryDependencies ++= Seq(
  "org.specs2"          %%  "specs2"            % "2.3.11"    % "test",
  "org.scalatest"       %   "scalatest_2.10"    % "2.1.3"     % "test",
  "org.mockito"         %   "mockito-core"      % "1.9.5"     % "test",
  "junit"               %   "junit"             % "4.11"      % "test"
)

Revolver.settings.settings

ScoverageSbtPlugin.instrumentSettings

org.scalastyle.sbt.ScalastylePlugin.Settings

unmanagedSourceDirectories in Test := (scalaSource in Test).value :: Nil

EclipseKeys.withSource := true

ideaExcludeFolders += ".idea"

ideaExcludeFolders += ".idea_modules"

fork := true
