import io.gatling.sbt.GatlingPlugin
import sbt._

object CopygrinderGatlingBuild extends Build {

  lazy val root: Project = Project(id = "gatling", base = file(".")
  ).configs(IntegrationTest)
    .enablePlugins(GatlingPlugin)
}