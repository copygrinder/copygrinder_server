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
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
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