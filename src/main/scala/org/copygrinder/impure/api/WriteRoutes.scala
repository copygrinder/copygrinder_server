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

import java.io.{IOException, PrintWriter, StringWriter}

import akka.actor.{ActorContext, ActorRefFactory}
import com.fasterxml.jackson.core.JsonParseException
import org.copygrinder.impure.copybean.controller.{BeanController, FileController, TypeController}
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import spray.http.MultipartContent
import spray.http.StatusCodes._
import spray.routing._

trait WriteRoutes extends RouteSupport with JsonReads with JsonWrites {

  val typeController: TypeController

  val beanController: BeanController

  val fileController: FileController

  val actorContext: ActorContext

  protected def writeExceptionHandler() =
    ExceptionHandler {
      case ex: Exception => {
        val sw = new StringWriter()
        ex.printStackTrace(new PrintWriter(sw))
        logger.debug(sw.toString)
        ex match {
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
          case e: SiloAlreadyInitialized =>
            val siloId = e.siloId
            complete(BadRequest, s"Silo with id '$siloId' already exists.")
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
    } ~ BuildRoute(siloPath & post)(implicit siloScope => {
      typeController.createSilo()
      beanController.createSilo()
    })
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
    } ~ BuildRoute(filePath & put & entity(as[MultipartContent])) { implicit siloScope =>
      (data) => {
        fileController.storeFile(data)
      }
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
