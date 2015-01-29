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
 siloScope: SiloScopeFactory
 ) extends ReadRoutes with WriteRoutes {

  override implicit def ac: ActorContext = _ac

  override val typeController: TypeController = _typeController

  override val beanController: BeanController = _beanController

  override val fileController: FileController = _fileController

  override val siloScopeFactory: SiloScopeFactory = siloScope

  override val securityController: SecurityController = _securityController

  val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes

}