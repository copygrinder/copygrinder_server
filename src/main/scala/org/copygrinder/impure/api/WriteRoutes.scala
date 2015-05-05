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

import java.io.{PrintWriter, StringWriter}

import com.fasterxml.jackson.core.JsonParseException
import org.copygrinder.impure.copybean.controller._
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import spray.http.StatusCodes._
import spray.http.{FormData, MultipartContent, Uri}
import spray.routing._
import spray.routing.authentication.BasicAuth

trait WriteRoutes extends RouteSupport with JsonReads with JsonWrites {

  val typeController: TypeController

  val beanController: BeanController

  val fileController: FileController

  protected def postRoutes = {
    BuildRoute(copybeansTypesPath & post & entity(as[Seq[CopybeanType]])) {
      implicit siloScope => (params, typeSeq) =>
        typeController.store(typeSeq, params)
    } ~ BuildRoute(copybeansTypesPath & post & entity(as[CopybeanType])) {
      implicit siloScope => (params, copybeanType) =>
        typeController.store(Seq(copybeanType), params)
    } ~ BuildRoute(copybeansPath & post & entity(as[Seq[AnonymousCopybean]])) {
      implicit siloScope => (params, anonBeans) =>
        beanController.store(anonBeans, params)
    } ~ BuildRoute(copybeansPath & post & entity(as[AnonymousCopybean])) {
      implicit siloScope => (params, anonBean) =>
        beanController.store(Seq(anonBean), params)
    } ~ BuildRoute(siloPath & post)(implicit siloScope => params => {
      val result = beanController.createSilo()
      fileController.createSilo()
      result
    }) ~ BuildRoute(filePath & post & entity(as[MultipartContent])) { implicit siloScope => (params, data) =>
      fileController.storeFile(data, params)
    }
  }

  protected val putRoutes = {
    BuildRoute(copybeansTypeIdPath & put & entity(as[CopybeanType])) {
      implicit siloScope: SiloScope => (id, params, copybeanType) =>
        typeController.update(copybeanType, params)
        ""
    } ~ BuildRoute(copybeansIdPath & put & entity(as[AnonymousCopybean])) {
      implicit siloScope: SiloScope => (id, params, copybean) => {
        beanController.update(id, copybean, params)
      }
    } ~ BuildRoute(passwordPath & put & entity(as[FormData])) {
      implicit siloScope: SiloScope => (params, form) =>
        val password = form.fields.find(_._1 == "password").get._2
        securityController.updatePassword(password)
        ""
    }
  }

  protected val deleteRoutes = {
    BuildRoute(copybeansTypeIdPath & delete) {
      implicit siloScope: SiloScope => (id, params) =>
        typeController.delete(id, params)
    } ~ BuildRoute(copybeansIdPath & delete) {
      implicit siloScope: SiloScope => (id, params) =>
        beanController.delete(id, params)
    }
  }

  protected val writeRoutes = postRoutes ~ putRoutes ~ deleteRoutes

  protected val adminRoutes = (adminPathPartial & pathPrefix("api")) { siloId =>
    authenticate(BasicAuth(authenticator(_), "Secured")) { username =>
      writeRoutes.compose(requestContext => {
        val newUri = Uri("/" + siloId + requestContext.unmatchedPath.toString).path
        requestContext.copy(unmatchedPath = newUri)
      })
    }
  }

  protected val writeInnerRoutes = adminRoutes ~ writeRoutes

  val copygrinderWriteRoutes: Route =
    authenticate(BasicAuth(authenticator(_), "Secured")) { username =>
      writeInnerRoutes
    }

}
