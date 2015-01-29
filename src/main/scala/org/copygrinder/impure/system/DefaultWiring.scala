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
import org.copygrinder.impure.copybean.controller.{SecurityController, BeanController, FileController, TypeController}
import org.copygrinder.impure.copybean.persistence._
import org.copygrinder.impure.copybean.search.Indexer
import org.copygrinder.pure.copybean.CopybeanReifier
import org.copygrinder.pure.copybean.model.{CopybeanType, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.search.{DocumentBuilder, QueryBuilder}
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
    persistenceServiceModule.documentBuilder, persistenceServiceModule.queryBuilder, globalModule.configuration
  )

  lazy val typeController = new TypeController(persistenceServiceModule.typePersistenceService)

  lazy val beanController = new BeanController(persistenceServiceModule.copybeanPersistenceService)

  lazy val fileController = new FileController(persistenceServiceModule.filePersistenceService,
    persistenceServiceModule.copybeanPersistenceService)

  lazy val securityController = new SecurityController(globalModule.configuration)

  def copygrinderApiFactory(ac: ActorContext): CopygrinderApi = {
    new CopygrinderApi(ac, typeController, beanController, fileController, securityController, siloScopeFactory)
  }

  lazy val routeExecutingActor = Props(new RouteExecutingActor(copygrinderApiFactory))

  lazy val routingActor = Props(new RoutingActor(routeExecutingActor, globalModule.configuration))

  lazy val serverInit = new ServerInit(globalModule.configuration, routingActor)

}


class PersistenceServiceModule(globalModule: GlobalModule) {

  lazy val hashedFileResolver = new HashedFileResolver()

  lazy val idEncoderDecoder = new IdEncoderDecoder()

  lazy val documentBuilder = new DocumentBuilder()

  lazy val queryBuilder = new QueryBuilder()

  lazy val copybeanTypeEnforcer = new CopybeanTypeEnforcer()

  lazy val copybeanReifier = new CopybeanReifier()

  lazy val predefinedCopybeanTypes = new PredefinedCopybeanTypes()

  lazy val typeEnforcer = new TypeEnforcer()

  lazy val typePersistenceService = new TypePersistenceService(predefinedCopybeanTypes, typeEnforcer)

  lazy val predefinedCopybeans = new PredefinedCopybeans()

  lazy val copybeanPersistenceService = new CopybeanPersistenceService(
    hashedFileResolver,
    copybeanTypeEnforcer,
    idEncoderDecoder,
    copybeanReifier,
    predefinedCopybeanTypes,
    predefinedCopybeans
  )

  lazy val filePersistenceService = new FilePersistenceService(
    hashedFileResolver
  )

}

class SiloScope(_siloId: String, documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, config: Configuration) {

  val siloId = _siloId

  lazy val root = new File(new File(config.copybeanDataRoot), siloId)

  lazy val indexDir = new File(root, "index/")

  lazy val indexer = new Indexer(indexDir, documentBuilder, queryBuilder, config.indexMaxResults)

  lazy val beanCache: Cache[ReifiedCopybean] = LruCache()

  lazy val typeCache: Cache[CopybeanType] = LruCache()

  lazy val beanDir = new File(root, "copybeans/")

  lazy val beanGitRepo = new GitRepo(beanDir, new FileRepositoryBuilderWrapper())

  lazy val typesDir = new File(root, "types/")

  lazy val typeGitRepo = new GitRepo(typesDir, new FileRepositoryBuilderWrapper())

  lazy val tempDir = new File(root, "temp/")

  lazy val fileDir = new File(root, "files/")

}

class SiloScopeFactory(documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, config: Configuration) {

  lazy val siloScopeCache: Cache[SiloScope] = LruCache()

  def build(siloId: String)(implicit ex: ExecutionContext): SiloScope = {
    val future = siloScopeCache(siloId) {
      new SiloScope(siloId, documentBuilder, queryBuilder, config)
    }
    Await.result(future, 5 seconds)
  }

}
