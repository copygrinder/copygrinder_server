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
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.copybean.persistence.{FileRepositoryBuilderWrapper, GitRepo, HashedFileResolver, PersistenceService}
import org.copygrinder.impure.copybean.search.Indexer
import org.copygrinder.pure.copybean.model.Copybean
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder
import org.copygrinder.pure.copybean.search.DocumentBuilder
import spray.caching.{Cache, LruCache}

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

  def copygrinderApiFactory(ac: ActorContext): CopygrinderApi = {
    new CopygrinderApi(ac, persistenceServiceModule.persistenceService)
  }

  lazy val routeExecutingActor = Props(new RouteExecutingActor(copygrinderApiFactory))

  lazy val routingActor = Props(new RoutingActor(routeExecutingActor, globalModule.configuration))

  lazy val serverInit = new ServerInit(globalModule.configuration, routingActor)

}


class PersistenceServiceModule(globalModule: GlobalModule) {

  lazy val hashedFileResolver = new HashedFileResolver()

  lazy val idEncoderDecoder = new IdEncoderDecoder()

  lazy val copybeanFactory = new CopybeanFactory(idEncoderDecoder)

  lazy val documentBuilder = new DocumentBuilder()

  lazy val indexer = new Indexer(globalModule.configuration, documentBuilder)

  lazy val cache: Cache[Copybean] = LruCache()

  lazy val fileRepositoryBuilderWrapper = new FileRepositoryBuilderWrapper()

  def gitRepoFactory(file: File): GitRepo = {
    new GitRepo(file, fileRepositoryBuilderWrapper)
  }

  lazy val persistenceService = new PersistenceService(
    globalModule.configuration, hashedFileResolver, copybeanFactory, indexer, cache, gitRepoFactory
  )

}