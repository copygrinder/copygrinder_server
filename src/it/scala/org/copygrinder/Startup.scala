/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.copygrinder

import akka.actor.ActorContext
import dispatch.Defaults._
import dispatch._
import org.copygrinder.impure.api.CopygrinderApi
import org.copygrinder.impure.system._
import org.scalatest.{FlatSpec, Matchers}
import spray.routing.Route

import scala.concurrent.Await
import scala.concurrent.duration._


class Startup extends FlatSpec with Matchers {

  val wiring = new DefaultWiring() {
    override lazy val globalModule = new GlobalModule {
      override lazy val configuration = new Configuration {
        override lazy val serviceReadPort = 9999
        override lazy val serviceWritePort = 9999
        override lazy val serviceThreads = 1
      }
    }
    override lazy val serverModule = new ServerModule(globalModule, persistenceServiceModule) {

      override def copygrinderApiFactory(ac: ActorContext): CopygrinderApi = {
        new CopygrinderApi(ac, persistenceServiceModule.persistenceService, siloScopeFactory) {
          override val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes ~
            path("longpause") {
              get {
                complete {
                  Future {
                    Thread.sleep(500)
                    "LONG"
                  }
                }
              }
            } ~
            path("shortpause") {
              get {
                complete {
                  Future {
                    Thread.sleep(100)
                    "SHORT"
                  }
                }
              }
            }
        }
      }
    }
  }


  "ServerInit" should "start" in {

    Await.result(wiring.serverModule.serverInit.init, 3 second)

    val shortReq = url("http://localhost:9999/shortpause")

    val longReq = url("http://localhost:9999/longpause")

    Await.result(Http(shortReq OK as.String), 1 second)

    val time1 = System.nanoTime()

    val longFutures = 1.to(2).map { _ =>
      Http(longReq OK as.String)
    }

    val shortFutures = 1.to(10).map { _ =>
      Http(shortReq OK as.String)
    }

    val futureSeq = Future.sequence(longFutures ++ shortFutures)
    Await.result(futureSeq, 10 second)

    val time2 = System.nanoTime()
    val duration = (time2 - time1) / 1000 / 1000

    assert(duration < 1000 + 200)

  }

}