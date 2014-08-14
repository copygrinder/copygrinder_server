package org.copygrinder.gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class GatlingTest extends Simulation {

  val httpConf = http
    .baseURL("http://localhost:8080")
    .acceptHeader("application/json")

  val scn = scenario("BasicSimulation")
    .exec(http("request_1")
    .get("/copybeans?field=name&phrase=Joe2"))

  val scn2 = scenario("R2")
    .exec(http("request_2")
    .get("/copybeans?field=name&phrase=Joe3"))

  setUp(
    scn2.inject(
      atOnceUsers(1)
    ),
    scn.inject(
      nothingFor(2 second),
      rampUsersPerSec(1).to(100).during(10 seconds)
    )
  ).protocols(httpConf)

}