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
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.softwaremill.macwire.MacwireMacros._
import spray.can.Http

import scala.concurrent.duration._


object Boot extends App {

  protected lazy val config = wire[Configuration]

  implicit val system = ActorSystem("copygrinder-system")

  val deadLetterActor = system.actorOf(Props(classOf[DeadLetterActor]))
  system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])

  if (config.serviceReadPort == config.serviceWritePort) {
    startCopygrinder("copygrinder-service-actor", config.serviceReadPort)
  } else {
    startCopygrinder("copygrinder-read-service-actor", config.serviceReadPort)
    startCopygrinder("copygrinder-write-service-actor", config.serviceWritePort)
  }

  protected def startCopygrinder(serviceName: String, port: Int) = {
    implicit val bindingTimeout: Timeout = 1 second
    val serviceActor = system.actorOf(Props[MyServiceActor], name = serviceName)
    IO(Http) ? Http.Bind(serviceActor, config.serviceHost, port = port)
  }

}