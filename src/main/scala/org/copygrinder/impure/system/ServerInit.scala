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

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http

import scala.concurrent.Future
import scala.concurrent.duration._


class ActorSystemInit() {
  def init(): ActorSystem = {
    val system = ActorSystem("copygrinder-system")

    //val deadLetterActor = system.actorOf(Props(classOf[DeadLetterActor]))
    //system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])

    system
  }
}

class ServerInit(config: Configuration, routingActor: Props)(implicit system: ActorSystem) extends SslConfig {

  override val keyStoreLocation = config.keyStoreLocation

  override val keyStorePassword = config.keyStorePassword

  def init: Future[Any] = {

    if (config.serviceReadPort == config.serviceWritePort) {
      startCopygrinder("copygrinder-service-actor", config.serviceReadPort)
    } else {
      startCopygrinder("copygrinder-read-service-actor", config.serviceReadPort)
      startCopygrinder("copygrinder-write-service-actor", config.serviceWritePort)
    }
  }

  protected def startCopygrinder(serviceName: String, port: Int)(implicit system: ActorSystem) = {
    implicit val bindingTimeout: Timeout = 5 seconds
    val serviceActor = system.actorOf(routingActor, name = serviceName)
    IO(Http) ? Http.Bind(serviceActor, config.serviceHost, port = port)
  }

}
