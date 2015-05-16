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
import org.copygrinder.impure.copybean.controller.{BeanController, FileController, TypeController}
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.persistence.JsonWrites
import spray.http.HttpHeaders._
import spray.http.StatusCodes._
import spray.http._
import spray.routing._
import spray.routing.authentication.BasicAuth

import scala.concurrent.Future

trait ReadRoutes extends RouteSupport with JsonWrites {

  val typeController: TypeController

  val beanController: BeanController

  val fileController: FileController

  val adminForceHttps: Boolean

  protected val rootRoute = siloPath { (silo, params) =>
    get {
      complete {
        "Copygrinder is running.  Check out the apis under /copybeans"
      }
    }
  } ~ pathEndOrSingleSlash {
    get {
      complete {
        "Copygrinder is running.  Check out the apis under /YOUR_SILO_NAME/copybeans"
      }
    }
  }

  protected val copybeanReadRoute = {
    BuildRoute(copybeansTypeIdPath & get) { implicit siloScope => (id, params) =>
      typeController.fetchCopybeanType(id, params)
    } ~ BuildRoute(copybeansTypesPath & get) { implicit siloScope => params =>
      typeController.findCopybeanTypes(params)
    } ~ BuildRoute(copybeansIdPath & get) { implicit siloScope => (id, params) =>
      beanController.fetchCopybean(id, params)
    } ~ BuildRoute(copybeansPath & get) { implicit siloScope => params =>
      beanController.find(params)
    } ~ BuildRoute(branchesPath & get) { implicit siloScope => (params) =>
      beanController.getBranches(params)
    } ~ BuildRoute(branchesIdPath & get) { implicit siloScope => (branchId, params) =>
      beanController.getCommitsByBranch(branchId, params)
    } ~ BuildRoute(branchHeadPath & get) { implicit siloScope => (branchId, params) =>
      beanController.getBranchHead(branchId, params)
    } ~ BuildRoute(branchHeadsPath & get) { implicit siloScope => (branchId, params) =>
      beanController.getBranchHeads(branchId, params)
    } ~ BuildRoute(copybeansIdHistoryPath & get) { implicit siloScope => (id, params) =>
      beanController.getHistoryById(id, params)
    } ~ BuildRoute(copybeansTypeIdHistoryPath & get) { implicit siloScope => (id, params) =>
      typeController.getHistoryById(id, params)
    } ~ copybeansIdFieldPath.&(get) { (siloId, beanId, fieldId, params) =>
      implicit lazy val siloScope = siloScopeFactory.build(siloId)
      onSuccess(
        Future {
          fileController.getFile(beanId, fieldId, params)
        }
      ) { fileData =>
        respondWithHeaders(`Content-Disposition`(fileData._4, Map("filename" -> fileData._1))) {
          respondWithMediaType(MediaType.custom(fileData._3)) {
            complete {
              fileData._2
            }
          }
        }
      }
    }
  }

  protected val adminReadRoute = {
    adminPath { (siloId, params) =>
      authenticate(BasicAuth(authenticator(_), "Secured")) { username =>
        adminIndex(siloId)
      }
    } ~ (adminPathPartial & pathPrefix("api")) { siloId =>
      authenticate(BasicAuth(authenticator(_), "Secured")) { username =>
        copybeanReadRoute.compose(requestContext => {
          val newUri = Uri("/" + siloId + requestContext.unmatchedPath.toString).path
          requestContext.copy(unmatchedPath = newUri)
        })
      }
    } ~ (adminPathPartial & get) { siloId =>
      unmatchedPath { unmatched =>
        authenticate(BasicAuth(authenticator(_), "Secured")) { username =>
          val resource = getClass.getClassLoader.getResource("admin" + unmatched.toString)
          // scalastyle:off null
          if (resource != null) {
            // scalastyle:on null
            getFromResource("admin" + unmatched.toString)
          } else {
            adminIndex(siloId)
          }
        }
      }
    }
  }

  protected def adminIndex(siloId: String) = {
    requestUri {
      uri =>
        futureComplete {
          val stream = getClass.getClassLoader.getResourceAsStream("admin/index.html")
          val scanner = new java.util.Scanner(stream).useDelimiter("\\A")
          val html = scanner.next()
          val uriString = uri.toString()
          val adminResource = s"/admin"
          val strippedUri = uriString.take(uriString.indexOf(adminResource) + adminResource.length)
          val newUrl = if (adminForceHttps) {
            strippedUri.replace("http:", "https:")
          } else {
            strippedUri
          }
          val newHtml = html.replace(
            """<base id="baseMetaTag" href="http://localhost:9000/" """ +
             """data-copygrinder-url="http://127.0.0.1:19836/integrationtest">""",
            s"""<base id="baseMetaTag" href="$newUrl/" data-copygrinder-url="$newUrl/api">"""
          )
          HttpEntity(MediaTypes.`text/html`, HttpData(newHtml))
        }
    }
  }

  protected val readInnerRoutes: Route = copybeanReadRoute ~ adminReadRoute

  val copygrinderReadRoutes: Route = rootRoute ~ readInnerRoutes

}
