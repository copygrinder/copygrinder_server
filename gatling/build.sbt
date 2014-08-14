organization  := "org.copygrinder.gatling"

version       := "0.1"

scalaVersion  := "2.10.4"

/* TEST LIBS */
libraryDependencies ++= Seq(
  "io.gatling.highcharts"     %  "gatling-charts-highcharts"    %  "2.0.0-RC2"  %  "test, it",
  "io.gatling"                %  "test-framework"               %  "1.0-RC2"    %  "test, it"
)
