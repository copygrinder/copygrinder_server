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

import com.softwaremill.macwire.MacwireMacros._
import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.copygrinder.impure.copybean.persistence.PersistenceService
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.http.StatusCodes._
import spray.httpx.Json4sJacksonSupport
import spray.routing._

import scala.concurrent._


trait CopygrinderApi extends HttpService with Json4sJacksonSupport with LazyLogging {

  protected lazy val persistenceService = wire[PersistenceService]

  override implicit def json4sJacksonFormats: Formats = DefaultFormats

  protected implicit def executionContext = actorRefFactory.dispatcher

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

  protected val copybeanRoute = handleExceptions(copybeanExceptionHandler) {
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
        }
      } ~
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

  def copygrinderRoutes: Route = rootRoute ~ copybeanRoute
}