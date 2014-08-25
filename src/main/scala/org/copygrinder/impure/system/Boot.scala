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
package org.copygrinder.impure.system

import akka.actor.{ActorSystem, DeadLetter, Props}
import com.softwaremill.macwire.MacwireMacros._
import org.copygrinder.impure.api.CopygrinderApi
import spray.routing.{Route, SimpleRoutingApp}

object Boot extends App with SimpleRoutingApp with CopygrinderApi {

  lazy val config = wire[Configuration]

  implicit val system = ActorSystem("copygrinder-system")

  val deadLetterActor = system.actorOf(Props(classOf[DeadLetterActor]))
  system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])

  if (config.serviceReadPort == config.serviceWritePort) {
    startCopygrinder("copygrinder-service-actor", config.serviceReadPort) {
      copygrinderReadRoutes ~ copygrinderWriteRoutes
    }
  } else {
    startCopygrinder("copygrinder-read-service-actor", config.serviceReadPort) {
      copygrinderReadRoutes
    }
    startCopygrinder("copygrinder-write-service-actor", config.serviceWritePort) {
      copygrinderWriteRoutes
    }
  }

  protected def startCopygrinder(serviceName: String, port: Int)(route: Route) = {
    startServer(serviceActorName = serviceName, interface = config.serviceHost, port = port)(route)
  }

}