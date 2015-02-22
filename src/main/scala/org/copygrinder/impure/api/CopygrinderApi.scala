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

import akka.actor.ActorContext
import org.copygrinder.impure.copybean.controller._
import org.copygrinder.impure.system.SiloScopeFactory
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

  lazy val wrappedAllCopygrinderRoutes: Route = cors(handleExceptions(writeExceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      innerCors {
        allCopygrinderRoutes
      }
    }
  })

  lazy val wrappedCopygrinderReadRoutes: Route = cors(handleExceptions(writeExceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      innerCors {
        copygrinderReadRoutes
      }
    }
  })

  lazy val wrappedCopygrinderWriteRoutes: Route = cors(handleExceptions(writeExceptionHandler) {
    handleRejections(RejectionHandler.Default) {
      copygrinderWriteRoutes
    }
  })

}