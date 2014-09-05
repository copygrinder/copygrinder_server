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

import akka.actor.{ActorContext, Actor, Props}
import akka.routing.BalancingPool
import org.copygrinder.impure.api.CopygrinderApi
import spray.http.{HttpRequest, HttpResponse, StatusCodes, Timedout}
import spray.routing._


class RoutingActor(routeExecutingActor: Props, config: Configuration) extends Actor {

  lazy val actorPool = context.actorOf(
    routeExecutingActor.withRouter(BalancingPool(config.serviceThreads))
    , name = self.path.name
  )

  protected implicit def executionContext = context.dispatcher

  override def receive = {
    case message => {
      actorPool forward message
    }
  }

}

class RouteExecutingActor(apiFactory: (ActorContext) => CopygrinderApi) extends Actor with HttpService {

  override def actorRefFactory = context

  val api = apiFactory(context)

  protected val route = context.parent.path.name match {
    case "copygrinder-service-actor" => api.allCopygrinderRoutes
    case "copygrinder-read-service-actor" => api.copygrinderReadRoutes
    case "copygrinder-write-service-actor" => api.copygrinderWriteRoutes
  }

  override def receive = {
    //handleTimeouts orElse runRoute(route)
    runRoute(route)
  }

  protected def handleTimeouts: Receive = {
    case Timedout(x: HttpRequest) =>
      sender ! HttpResponse(StatusCodes.InternalServerError, "Too late")
  }

}
