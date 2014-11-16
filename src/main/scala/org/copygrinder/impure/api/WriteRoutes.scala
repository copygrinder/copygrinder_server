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

import com.fasterxml.jackson.core.JsonParseException
import org.copygrinder.impure.copybean.persistence.PersistenceService
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, CopybeanImpl, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import spray.http.StatusCodes._
import spray.routing._

trait WriteRoutes extends RouteSupport with JsonReads with JsonWrites {

  val persistenceService: PersistenceService

  protected def writeExceptionHandler() =
    ExceptionHandler {
      case e: CopybeanNotFound =>
        val id = e.id
        logger.debug(s"Copybean with id=$id was not found")
        complete(NotFound, s"Copybean with id '$id' was not found.")
      case e: CopybeanTypeNotFound =>
        val id = e.id
        logger.debug(s"Copybean Type with id=$id was not found")
        complete(NotFound, s"Copybean Type with id '$id' was not found.")
      case e: SiloNotInitialized =>
        val siloId = e.siloId
        logger.debug(s"Silo with id=$siloId does not exist.")
        complete(NotFound, s"Silo with id=$siloId does not exist.")
      case e: TypeValidationException =>
        complete(BadRequest, e.getMessage)
      case e: JsonInputException =>
        complete(BadRequest, e.getMessage)
      case e: JsonParseException =>
        complete(BadRequest, e.getMessage)
      case e: IOException =>
        requestUri { uri =>
          logger.error(s"Error occurred while processing request to $uri", e)
          complete(InternalServerError, "Error occurred")
        }
    }

  protected val postRoutes = handleExceptions(writeExceptionHandler) {
    pathPrefix(Segment) { siloId =>
      post {
        pathPrefix("copybeans") {
          entity(as[Seq[AnonymousCopybean]]) { anonBeans =>
            scopedComplete(siloId) { implicit siloScope =>
              anonBeans.map { anonBean =>
                persistenceService.store(anonBean)
              }
            }
          } ~
           entity(as[AnonymousCopybean]) { anonBean =>
             scopedComplete(siloId) { implicit siloScope =>
               persistenceService.store(anonBean)
             }
           } ~ path("types") {
            entity(as[Seq[CopybeanType]]) { copybeanTypes =>
              scopedComplete(siloId) { implicit siloScope =>
                copybeanTypes.map { copybeanType =>
                  persistenceService.store(copybeanType)
                }
                ""
              }
            } ~
             entity(as[CopybeanType]) { copybeanType =>
               scopedComplete(siloId) { implicit siloScope =>
                 persistenceService.store(copybeanType)
                 ""
               }
             }
          }
        }
      }
    }
  }

  protected val putRoutes = handleExceptions(writeExceptionHandler) {
    pathPrefix(Segment) { siloId =>
      put {
        pathPrefix("copybeans") {
          path(Segment) { id =>
            entity(as[AnonymousCopybean]) { copybean =>
              scopedComplete(siloId) { implicit siloScope =>
                persistenceService.update(id, copybean)
                ""
              }
            }
          } ~ pathPrefix("types") {
            path(Segment) { id =>
              entity(as[CopybeanType]) { copybeanTypes =>
                scopedComplete(siloId) { implicit siloScope =>
                  persistenceService.update(copybeanTypes)
                  ""
                }
              }
            }
          }
        }
      }
    }
  }

  val copygrinderWriteRoutes: Route = cors(postRoutes ~ putRoutes)

}
