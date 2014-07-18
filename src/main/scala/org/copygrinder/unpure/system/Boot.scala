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
package org.copygrinder.unpure.system

import akka.actor.{DeadLetter, ActorSystem, Props, actorRef2Scala}
import org.copygrinder.unpure.api.CopygrinderApi
import spray.routing.SimpleRoutingApp

object Boot extends App with SimpleRoutingApp with CopygrinderApi  {

  implicit val system = ActorSystem("copygrinder-system")

  val deadLetterActor = system.actorOf(Props(classOf[DeadLetterActor]))
  system.eventStream.subscribe(deadLetterActor, classOf[DeadLetter])

  startServer(interface = Configuration.serviceHost, port = Configuration.servicePort) {
    copygrinderRoutes
  }

}