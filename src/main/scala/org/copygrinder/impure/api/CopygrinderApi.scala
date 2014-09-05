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
package org.copygrinder.impure.api

import java.io.IOException

import akka.actor.ActorContext
import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.copybean.persistence.PersistenceService
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.http.StatusCodes._
import spray.httpx.Json4sJacksonSupport
import spray.routing._

import scala.concurrent._

class CopygrinderApi(ac: ActorContext, persistenceService: PersistenceService) extends Directives with Json4sJacksonSupport with LazyLogging with CorsSupport {

  override implicit def json4sJacksonFormats: Formats = DefaultFormats

  private implicit def executionContext = ac.dispatcher

  protected def copybeanExceptionHandler() =
    ExceptionHandler {
      case e: CopybeanNotFound =>
        pathPrefix("copybeans") {
          path(Segment) { id =>
            logger.debug(s"Copybean with id=$id was not found")
            complete(NotFound, s"Copy bean with id '$id' was not found.")
          }
        }
      case e: IOException =>
        requestUri { uri =>
          logger.error(s"Error occurred while processing request to $uri", e)
          complete(InternalServerError, "Error occurred")
        }
    }

  protected val rootRoute = path("") {
    get {
      complete {
        val jsonValues = parse( """{"name":"joe","age":15}""").extract[JObject]
        new Copybean("bean1", Set("hi"), jsonValues)
      }
    }
  }

  protected val copybeanReadRoute = handleExceptions(copybeanExceptionHandler) {
    pathPrefix("copybeans") {
      get {
        parameters('field, 'phrase) { (field, phrase) =>
          complete {
            Future {
              persistenceService.find(field, phrase)
            }
          }
        } ~ path(Segment) { id =>
          complete {
            Future {
              persistenceService.cachedFetch(id)
            }
          }
        } ~ complete {
          Future {
            persistenceService.find()
          }
        }
      }
    }
  }

  protected val copybeanWriteRoute = handleExceptions(copybeanExceptionHandler) {
    pathPrefix("copybeans") {
      post {
        entity(as[AnonymousCopybean]) { anonBean =>
          complete {
            Future {
              persistenceService.store(anonBean).map(("key", _))
            }
          }
        }
      }
    }
  }

  val copygrinderReadRoutes: Route = cors(rootRoute ~ copybeanReadRoute)

  val copygrinderWriteRoutes: Route = cors(copybeanWriteRoute)

  val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes

}