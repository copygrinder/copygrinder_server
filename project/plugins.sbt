resolvers += Resolver.url("jetbrains-bintray",
  url("http://dl.bintray.com/jetbrains/sbt-plugins/"))(Resolver.ivyStylePatterns)

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.4.0")

addSbtPlugin("org.jetbrains" % "sbt-ide-settings" % "0.1.0")

addSbtPlugin("com.timushev.sbt" % "sbt-updates" % "0.1.8")

addSbtPlugin("org.scoverage" %% "sbt-scoverage" % "1.0.4")

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "0.6.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.13.0")

addSbtPlugin("com.orrsella" % "sbt-stats" % "1.0.5")
