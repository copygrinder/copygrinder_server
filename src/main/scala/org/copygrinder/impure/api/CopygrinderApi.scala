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
import com.fasterxml.jackson.core.JsonParseException
import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.copybean.persistence.PersistenceService
import org.copygrinder.impure.system.{SiloScope, SiloScopeFactory}
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json._
import spray.http.HttpRequest
import spray.http.StatusCodes._
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling.{Deserialized, FromRequestUnmarshaller, MalformedContent}
import spray.routing._

import scala.concurrent._

class CopygrinderApi(ac: ActorContext, persistenceService: PersistenceService, siloScopeFactory: SiloScopeFactory)
  extends Directives with PlayJsonSupport with JsonWrites with JsonReads with LazyLogging with CorsSupport {

  private implicit def executionContext = ac.dispatcher


  protected implicit def unmarshaller[T](implicit r: Reads[T]) = new FromRequestUnmarshaller[T] {
    override def apply(req: HttpRequest): Deserialized[T] = {
      val json = Json.parse(req.entity.data.toByteArray)
      r.reads(json) match {
        case s: JsSuccess[T] => Right(s.get)
        case e: JsError => Left(new MalformedContent(JsError.toFlatJson(e).toString()))
      }
    }
  }

  protected def copybeanExceptionHandler() =
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
        logger.debug(s"Silo with id=$siloId has not been initialized")
        complete(NotFound, s"Silo with id '$siloId' has not been initialized.")
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

  protected val rootRoute = path("") {
    get {
      complete {
        "Copygrinder is running.  Check out the apis under /copybeans"
      }
    }
  }

  protected val copybeanReadRoute = handleExceptions(copybeanExceptionHandler) {

    pathPrefix(Segment) {
      siloId =>
        get {
          pathPrefix("copybeans") {
            pathPrefix("types") {
              parameterSeq {
                params =>
                  if (params.isEmpty || params.head._1.isEmpty) {
                    reject
                  } else {
                    scopedComplete(siloId) { implicit siloScope =>
                      persistenceService.findCopybeanTypes(params)
                    }
                  }
              } ~ path(Segment) {
                id =>
                  scopedComplete(siloId) { implicit siloScope =>
                    persistenceService.cachedFetchCopybeanType(id)
                  }
              } ~ scopedComplete(siloId) { implicit siloScope =>
                persistenceService.fetchAllCopybeanTypes()
              }
            } ~ parameterSeq {
              params =>
                if (params.isEmpty || params.head._1.isEmpty) {
                  reject
                } else {
                  scopedComplete(siloId) { implicit siloScope =>
                    persistenceService.find(params)
                  }
                }
            } ~ path(Segment) {
              id =>
                scopedComplete(siloId) { implicit siloScope =>
                  persistenceService.cachedFetchCopybean(id)
                }
            } ~ scopedComplete(siloId) { implicit siloScope =>
              persistenceService.find()
            }
          }
        }
    }
  }

  protected val copybeanWriteRoute = handleExceptions(copybeanExceptionHandler) {
    pathPrefix(Segment) {
      siloId =>
        post {
          pathPrefix("copybeans") {
            path("types") {
              entity(as[Seq[CopybeanType]]) { copybeanTypes =>
                scopedComplete(siloId) { implicit siloScope =>
                  copybeanTypes.map {
                    copybeanType =>
                      persistenceService.store(copybeanType)
                  }
                  ""
                }
              } ~ entity(as[CopybeanType]) { copybeanType =>
                scopedComplete(siloId) { implicit siloScope =>
                  persistenceService.store(copybeanType)
                  ""
                }
              }
            } ~ entity(as[AnonymousCopybean]) {
              anonBean =>
                scopedComplete(siloId) { implicit siloScope =>
                  persistenceService.store(anonBean)
                }
            } ~ entity(as[Seq[AnonymousCopybean]]) {
              anonBeans =>
                scopedComplete(siloId) { implicit siloScope =>
                  anonBeans.map {
                    anonBean =>
                      persistenceService.store(anonBean)
                  }
                }
            }
          }
        }
    }
  }

  def scopedComplete[T](siloId: String)(body: => (SiloScope) => T)(implicit w: Writes[T]): StandardRoute = {
    lazy val siloScope = siloScopeFactory.build(siloId)
    futureComplete(Json.toJson(body(siloScope)))
  }

  def futureComplete[T]: (=> ToResponseMarshallable) => StandardRoute = (marshallable) => {
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