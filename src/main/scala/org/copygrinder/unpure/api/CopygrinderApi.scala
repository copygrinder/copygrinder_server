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
package org.copygrinder.unpure.api

import akka.actor.{ActorLogging, Actor}
import org.json4s.{Formats, DefaultFormats}
import spray.httpx.{Json4sJacksonSupport, Json4sSupport}
import spray.routing._
import spray.http._
import spray.http.MediaTypes._
import spray.routing.Directive.pimpApply
import org.copygrinder.pure.copybean.Copybean

case class Panda(name: String)

// this trait defines our service behavior independently from the service actor
trait CopygrinderApi extends HttpService with Json4sJacksonSupport {

  // we use the enclosing ActorContext's or ActorSystem's dispatcher for our Futures and Scheduler
  implicit def executionContext = actorRefFactory.dispatcher

  override implicit def json4sJacksonFormats: Formats = DefaultFormats

  val rootRoute = path("") {
    get {
      complete {
        new Copybean()
      }
    }
  }

  val copygrinderRoutes = rootRoute
}