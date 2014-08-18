package org.copygrinder.gatling

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.config.HttpProtocol
import scala.concurrent.duration._

class GatlingTest extends Simulation {

  val httpConf = http
    .baseURL("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  val scn = scenario("Search")
    .exec(http("request_1")
    .get("/copybeans?field=testValue1&phrase=abc"))

  val json = """{"enforcedTypeIds": [], "contains": {"testValue1":"abc", "testValue2": "123"}}"""

  val populate = scenario("Populate").exec(
    http("request_populate")
      .post("/copybeans")
      .body(StringBody(json))
  )

  setUp(
    /*populate.inject(
      heavisideUsers(100) over  (5 seconds)
    )
    scn.copy("warmupSearch").inject(
      atOnceUsers(1)
    ),*/
    scn.inject(
      //nothingFor(2 second),
      //rampUsersPerSec(1).to(500).during(10 seconds)
      atOnceUsers(10000)
    )
  ).protocols(httpConf)

}