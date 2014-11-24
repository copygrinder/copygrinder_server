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
import org.copygrinder.impure.copybean.controller.{BeanController, TypeController}
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.persistence.JsonWrites
import spray.http.StatusCodes._
import spray.routing._

trait ReadRoutes extends RouteSupport with JsonWrites {

  val typePersistenceService: TypeController

  val beanPersistenceService: BeanController

  protected def readExceptionHandler() =
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
        logger.debug(s"Silo with id=$siloId does not exist")
        complete(NotFound, s"Silo with id=$siloId does not exist.")
      case e: TypeValidationException =>
        complete(BadRequest, e.getMessage)
      case e: JsonInputException =>
        complete(BadRequest, e.getMessage)
      case e: JsonParseException =>
        complete(BadRequest, e.getMessage)
      case e: UnknownQueryParameter =>
        complete(BadRequest, "Unknown query parameter '" + e.param + "'.  Did you mean 'content." + e.param + "'?")
      case e: IOException =>
        requestUri { uri =>
          logger.error(s"Error occurred while processing request to $uri", e)
          complete(InternalServerError, "Error occurred")
        }
    }

  protected val rootRoute = path("") {
    get {
      complete {
        "Copygrinder is running.  Check out the apis under /copybeans"
      }
    }
  }

  protected val copybeanReadRoute = handleExceptions(readExceptionHandler) {

    pathPrefix(Segment) { siloId =>
      get {
        pathPrefix("copybeans") {
          pathPrefix("types") {
            parameterSeq { params =>
              if (params.isEmpty || params.head._1.isEmpty) {
                reject
              } else {
                scopedComplete(siloId) { implicit siloScope =>
                  typePersistenceService.findCopybeanTypes(params)
                }
              }
            } ~
             path(Segment) { id =>
               scopedComplete(siloId) { implicit siloScope =>
                 typePersistenceService.fetchCopybeanType(id)
               }
             } ~
             scopedComplete(siloId) { implicit siloScope =>
               typePersistenceService.fetchAllCopybeanTypes()
             }
          } ~
           parameterSeq { params =>
             if (params.isEmpty || params.head._1.isEmpty) {
               reject
             } else {
               scopedComplete(siloId) { implicit siloScope =>
                 beanPersistenceService.find(params)
               }
             }
           } ~
           path(Segment) { id =>
             scopedComplete(siloId) { implicit siloScope =>
               beanPersistenceService.cachedFetchCopybean(id)
             }
           } ~
           scopedComplete(siloId) { implicit siloScope =>
             beanPersistenceService.find()
           }
        }
      }
    }
  }

  val copygrinderReadRoutes: Route = cors(rootRoute ~ copybeanReadRoute)

}
