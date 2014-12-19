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
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsString, Writes}
import shapeless.{::, HNil}
import spray.http.StatusCodes._
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling.FromRequestUnmarshaller
import spray.routing._
import spray.routing.directives.MethodDirectives

trait WriteRoutes extends RouteSupport with JsonReads with JsonWrites {

  val typeController: TypeController

  val beanController: BeanController

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

  protected def postRoutes = {
    BuildRoute(copybeansTypesPath & post & entity(as[Seq[CopybeanType]])) {
      implicit siloScope: SiloScope => a: Seq[CopybeanType] => a.map { copybeanType =>
        typeController.store(copybeanType)
        ""
      }
    } ~ BuildRoute(copybeansTypesPath & post & entity(as[CopybeanType])) {
      implicit siloScope => (copybeanType) =>
        typeController.store(copybeanType)
        ""
    } ~ BuildRoute(copybeansPath & post & entity(as[Seq[AnonymousCopybean]])) {
      implicit siloScope: SiloScope => a: Seq[AnonymousCopybean] => a.map { anonBean =>
        beanController.store(anonBean)
      }
    } ~ BuildRoute(copybeansPath & post & entity(as[AnonymousCopybean])) {
      implicit siloScope => (anonBean) =>
        beanController.store(anonBean)
    }
  }


  protected val putRoutes = {
    BuildRoute(copybeansTypeIdPath & put & entity(as[CopybeanType])) {
      implicit siloScope: SiloScope => (id, copybeanType) =>
        typeController.update(copybeanType)
        ""
    } ~ BuildRoute(copybeansIdPath & put & entity(as[AnonymousCopybean])) {
      implicit siloScope: SiloScope => (id, copybean) =>
        beanController.update(id, copybean)
        ""
    }
  }

  protected val deleteRoutes = {
    BuildRoute(copybeansTypeIdPath & delete) {
      implicit siloScope: SiloScope => (id) =>
        typeController.delete(id)
    } ~ BuildRoute(copybeansIdPath & delete) {
      implicit siloScope: SiloScope => (id) =>
        beanController.delete(id)
    }
  }


  val copygrinderWriteRoutes: Route = handleExceptions(writeExceptionHandler) {
    cors(postRoutes ~ putRoutes ~ deleteRoutes)
  }

}