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
import org.copygrinder.impure.copybean.controller.{BeanController, TypeController}
import org.copygrinder.impure.copybean.persistence.{CopybeanPersistenceService, TypePersistenceService}
import org.copygrinder.impure.system.SiloScopeFactory
import spray.routing._

import scala.concurrent.ExecutionContext

class CopygrinderApi(
 ac: ActorContext,
 typePersistence: TypeController,
 _beanPersistence: BeanController,
 siloScope: SiloScopeFactory
 ) extends ReadRoutes with WriteRoutes {

  override implicit def executionContext: ExecutionContext = ac.dispatcher

  override val typeController: TypeController = typePersistence

  override val beanController: BeanController = _beanPersistence

  override val siloScopeFactory: SiloScopeFactory = siloScope

  val allCopygrinderRoutes: Route = copygrinderReadRoutes ~ copygrinderWriteRoutes

}