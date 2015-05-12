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

import akka.actor.ActorContext
import com.fasterxml.jackson.core.JsonParseException
import org.copygrinder.impure.copybean.controller._
import org.copygrinder.impure.system.SiloScopeFactory
import org.copygrinder.pure.copybean.exception.{CopygrinderThrowableException, CopygrinderNotFoundException, CopygrinderRuntimeException, CopygrinderInputException}
import spray.http.StatusCodes._
import spray.routing._

class CopygrinderApi(
 _ac: ActorContext,
 _typeController: TypeController,
 _beanController: BeanController,
 _fileController: FileController,
 _securityController: SecurityController,
 siloScope: SiloScopeFactory,
 _adminForceHttps: Boolean
 ) extends ReadRoutes with WriteRoutes with CorsSupport {

  override implicit def ac = _ac

  override val typeController = _typeController

  override val beanController = _beanController

  override val fileController = _fileController

  override val siloScopeFactory = siloScope

  override val securityController = _securityController

  override val adminForceHttps = _adminForceHttps

  val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes

  val exceptionHandler = ExceptionHandler {
    case ex: Exception =>
      val sw = new StringWriter()
      ex.printStackTrace(new PrintWriter(sw))
      logger.debug(sw.toString)
      ex match {
        case e: JsonParseException =>
          complete(BadRequest, e.getMessage)
        case e: CopygrinderInputException => complete(BadRequest, e.getMessage)
        case e: CopygrinderRuntimeException => complete(InternalServerError, e.getMessage)
        case e: CopygrinderThrowableException => complete(InternalServerError, e.getMessage)
        case e: CopygrinderNotFoundException => complete(Gone, e.getMessage)
        case e =>
          requestUri { uri =>
            logger.error(s"Error occurred while processing request to $uri", e)
            complete(InternalServerError, "Error occurred")
          }
      }
  }

  lazy val wrappedAllCopygrinderRoutes: Route = cors(handleExceptions(exceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      innerCors {
        allCopygrinderRoutes
      }
    }
  })

  lazy val wrappedCopygrinderReadRoutes: Route = cors(handleExceptions(exceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      innerCors {
        copygrinderReadRoutes
      }
    }
  })

  lazy val wrappedCopygrinderWriteRoutes: Route = cors(handleExceptions(exceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      copygrinderWriteRoutes
    }
  })

}