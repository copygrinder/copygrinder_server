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

import akka.actor.Actor
import org.copygrinder.impure.api.CopygrinderApi
import spray.routing._

class MyServiceActor extends Actor with HttpService {

  override def actorRefFactory = context

  override def receive = run(route)

  protected val api = new CopygrinderApi

  protected val route = self.path.name match {
    case "copygrinder-service-actor" => api.allCopygrinderRoutes
    case "copygrinder-read-service-actor" => api.copygrinderReadRoutes
    case "copygrinder-write-service-actor" => api.copygrinderWriteRoutes
  }

  protected def run(route: Route) = {
    runRoute(route)
  }

}