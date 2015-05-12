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
package org.copygrinder.framework

import akka.actor.ActorContext
import dispatch._
import org.copygrinder.impure.api.CopygrinderApi
import org.copygrinder.impure.system.{Configuration, DefaultWiring, GlobalModule, ServerModule}
import spray.routing._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

object TestWiring {

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
        new CopygrinderApi(
          ac, typeController, beanController, fileController, securityController, siloScopeFactory,
        globalModule.configuration.adminForceHttps) {
          implicit val ec: ExecutionContext = ac.dispatcher
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

  Await.result(wiring.serverModule.serverInit.init, 3 second)

}