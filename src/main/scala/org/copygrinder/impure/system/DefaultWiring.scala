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
package org.copygrinder.impure.system

import java.io.File

import akka.actor.{ActorContext, Props}
import org.copygrinder.impure.api.CopygrinderApi
import org.copygrinder.impure.copybean.controller.{BeanController, FileController, SecurityController, TypeController}
import org.copygrinder.impure.copybean.persistence._
import org.copygrinder.pure.copybean.persistence._
import spray.caching.{Cache, LruCache}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}

class DefaultWiring {

  lazy val globalModule = new GlobalModule

  lazy val persistenceServiceModule = new PersistenceServiceModule(globalModule)

  lazy val serverModule = new ServerModule(globalModule, persistenceServiceModule)

}


class GlobalModule() {

  lazy val configuration = new Configuration()

}


class ServerModule(globalModule: GlobalModule, persistenceServiceModule: PersistenceServiceModule) {
  val actorSystemInit = new ActorSystemInit()

  implicit val actorSystem = actorSystemInit.init()

  val siloScopeFactory = new SiloScopeFactory(
    globalModule.configuration,
    persistenceServiceModule
  )

  lazy val typeController = new TypeController(persistenceServiceModule.typePersistenceService)

  lazy val beanController = new BeanController(persistenceServiceModule.copybeanPersistenceService)

  lazy val fileController = new FileController(persistenceServiceModule.filePersistenceService,
    persistenceServiceModule.copybeanPersistenceService)

  lazy val securityController = new SecurityController(globalModule.configuration)

  def copygrinderApiFactory(ac: ActorContext): CopygrinderApi = {
    new CopygrinderApi(ac, typeController, beanController, fileController, securityController, siloScopeFactory,
      globalModule.configuration.adminForceHttps)
  }

  lazy val routeExecutingActor = Props(new RouteExecutingActor(copygrinderApiFactory))

  lazy val routingActor = Props(new RoutingActor(routeExecutingActor, globalModule.configuration))

  lazy val serverInit = new ServerInit(globalModule.configuration, routingActor)

}


class PersistenceServiceModule(globalModule: GlobalModule) {

  lazy val hashedFileResolver = new HashedFileResolver()

  lazy val idEncoderDecoder = new IdEncoderDecoder()

  lazy val copybeanTypeEnforcer = new CopybeanTypeEnforcer()

  lazy val predefinedCopybeanTypes = new PredefinedCopybeanTypes()

  lazy val typePersistenceService = new TypePersistenceService(predefinedCopybeanTypes)

  lazy val predefinedCopybeans = new PredefinedCopybeans()

  lazy val copybeanPersistenceService = new CopybeanPersistenceService(
    copybeanTypeEnforcer,
    idEncoderDecoder,
    predefinedCopybeanTypes,
    predefinedCopybeans
  )

  lazy val filePersistenceService = new FilePersistenceService(
    hashedFileResolver
  )

}

class SiloScope(
 _siloId: String,
 config: Configuration,
 predefinedCopybeans: PredefinedCopybeans,
 predefinedCopybeanTypes: PredefinedCopybeanTypes
 ) {

  val siloId = _siloId

  lazy val root = new File(new File(config.copybeanDataRoot), siloId)

  lazy val tempDir = new File(root, "temp/")

  lazy val fileDir = new File(root, "files/")

  lazy val persistor: VersionedDataPersistor = null

  lazy val defaultBranch = "master"

}

class SiloScopeFactory(
 config: Configuration,
 persistenceServiceModule: PersistenceServiceModule) {

  lazy val siloScopeCache: Cache[SiloScope] = LruCache()

  def build(siloId: String)(implicit ex: ExecutionContext): SiloScope = {
    val future = siloScopeCache(siloId) {
      new SiloScope(siloId, config, persistenceServiceModule.predefinedCopybeans,
        persistenceServiceModule.predefinedCopybeanTypes)
    }
    Await.result(future, 5 seconds)
  }

}
