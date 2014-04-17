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
package org.copygrinder.api

import akka.actor.Actor
import spray.routing._
import spray.http._
import spray.http.MediaTypes._
import spray.routing.Directive.pimpApply

// we don't implement our route structure directly in the service actor because
// we want to be able to test it independently, without having to spin up an actor
class CopygrinderServiceActor extends Actor with CopygrinderService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  override def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  override def receive = runRoute(myRoute)
}

// this trait defines our service behavior independently from the service actor
trait CopygrinderService extends HttpService {

  val copybeanGet = path("copybean" / HexIntNumber) & (get)

  val rootGet = path("") & (get)

  val myRoute =
    rootGet {
      respondWithMediaType(`text/html`) { // XML is marshalled to `text/xml` by default, so we simply override here
        complete {
          <html>
            <body>
              <h1>Hello Copygrinder!</h1>
            </body>
          </html>
        }
      }
    } ~
      copybeanGet { id =>
        ctx =>
          ctx.complete("Received " + ctx.request.method + " request for bean: " + id)
      }
}