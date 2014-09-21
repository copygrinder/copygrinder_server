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
import org.copygrinder.impure.system.{SiloScope, SiloScopeFactory}
import org.copygrinder.pure.copybean.exception.{CopybeanNotFound, SiloNotInitialized}
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean, CopybeanType}
import org.json4s.Formats
import org.json4s.JsonAST.JObject
import org.json4s.jackson.JsonMethods._
import spray.http.StatusCodes._
import spray.httpx.Json4sJacksonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.routing._

import scala.concurrent._

class CopygrinderApi(ac: ActorContext, persistenceService: PersistenceService, siloScopeFactory: SiloScopeFactory)
  extends Directives with Json4sJacksonSupport with LazyLogging with CorsSupport {

  override implicit def json4sJacksonFormats: Formats = persistenceService.json4sJacksonFormats

  private implicit def executionContext = ac.dispatcher

  protected def copybeanExceptionHandler() =
    ExceptionHandler {
      case e: CopybeanNotFound =>
        val id = e.id
        logger.debug(s"Copybean with id=$id was not found")
        complete(NotFound, s"Copybean with id '$id' was not found.")
      case e: SiloNotInitialized =>
        val siloId = e.siloId
        logger.debug(s"Silo with id=$siloId has not been initialized")
        complete(NotFound, s"Silo with id '$siloId' has not been initialized.")
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

    pathPrefix(Segment) {
      siloId =>
        get {
          pathPrefix("copybeans") {
            typesReadRoute(siloId) ~ copybeanGetRoute(siloId)
          }
        }
    }
  }

  protected def typesReadRoute(siloId: String) =
    path("types") {
      path(Segment) {
        id =>
          ScopedComplete(siloId) { implicit siloScope =>
            persistenceService.fetchCopybeanType(id)
          }
      } ~ ScopedComplete(siloId) { implicit siloScope =>
        "UMM"
      }
    }

  protected def copybeanGetRoute(siloId: String) = parameterSeq {
    params =>
      if (params.isEmpty || params.head._1.isEmpty) {
        reject
      } else {
        ScopedComplete(siloId) { implicit siloScope =>
          persistenceService.find(params)
        }
      }
  } ~ path(Segment) {
    id =>
      ScopedComplete(siloId) { implicit siloScope =>
        persistenceService.cachedFetchCopybean(id)
      }
  } ~ ScopedComplete(siloId) { implicit siloScope =>
    persistenceService.find()
  }

  protected val copybeanWriteRoute = handleExceptions(copybeanExceptionHandler) {
    pathPrefix(Segment) {
      siloId =>
        post {
          pathPrefix("copybeans") {
            path("types") {
              entity(as[CopybeanType]) { copybeanType =>
                ScopedComplete(siloId) { implicit siloScope =>
                  persistenceService.store(copybeanType)
                  ""
                }
              } ~ entity(as[Seq[CopybeanType]]) { copybeanTypes =>
                ScopedComplete(siloId) { implicit siloScope =>
                  copybeanTypes.map {
                    copybeanType =>
                      persistenceService.store(copybeanType)
                  }
                }
              }
            } ~ entity(as[Seq[AnonymousCopybean]]) {
              anonBeans =>
                ScopedComplete(siloId) { implicit siloScope =>
                  anonBeans.map {
                    anonBean =>
                      persistenceService.store(anonBean).map(("key", _))
                  }
                }
            } ~ entity(as[AnonymousCopybean]) {
              anonBean =>
                ScopedComplete(siloId) { implicit siloScope =>
                  persistenceService.store(anonBean).map(("key", _))
                }
            }
          }
        }
    }
  }

  protected object ScopedComplete {
    def apply[T](siloId: String)(body: => (SiloScope) => ToResponseMarshallable) = {
      lazy val siloScope = siloScopeFactory.build(siloId)
      futureComplete(body(siloScope))
    }
  }

  def futureComplete: (=> ToResponseMarshallable) => StandardRoute = (marshallable) => {
    complete(
      Future {
        marshallable
      }
    )
  }

  val copygrinderReadRoutes: Route = cors(rootRoute ~ copybeanReadRoute)

  val copygrinderWriteRoutes: Route = cors(copybeanWriteRoute)

  val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes

}