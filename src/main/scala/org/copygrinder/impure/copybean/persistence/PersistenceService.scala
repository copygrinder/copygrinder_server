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
package org.copygrinder.impure.copybean.persistence

import java.io.File
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.system.{Configuration, SiloScope}
import org.copygrinder.pure.copybean.CopybeanReifier
import org.copygrinder.pure.copybean.exception.{JsonInputException, CopybeanNotFound, CopybeanTypeNotFound, SiloNotInitialized}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class PersistenceService(
 config: Configuration,
 hashedFileResolver: HashedFileResolver,
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 copybeanReifier: CopybeanReifier,
 predefinedCopybeanTypes: PredefinedCopybeanTypes
 ) extends LazyLogging with JsonReads with JsonWrites {

  def fetchCopybean(id: String)(implicit siloScope: SiloScope): ReifiedCopybean = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)

    val copybean = if (!file.exists()) {
      throw new CopybeanNotFound(id)
    } else {
      val json = FileUtils.readFileToByteArray(file)
      implicitly[Reads[CopybeanImpl]].reads(Json.parse(json)).get
    }

    val future = copybean.enforcedTypeIds.map { typeId =>
      cachedFetchCopybeanType(typeId)
    }
    val futureSeq = Future.sequence(future)
    val types = Await.result(futureSeq, 5 seconds)
    copybeanReifier.reify(copybean, types)
  }

  def cachedFetchCopybean(id: String)(implicit siloScope: SiloScope): Future[ReifiedCopybean] = siloScope.beanCache(id) {
    fetchCopybean(id)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): String = {
    val id = idEncoderDecoder.encodeUuid(UUID.randomUUID())
    val copybean = new CopybeanImpl(anonCopybean.enforcedTypeIds, anonCopybean.content, id)
    store(copybean)
  }

  def store(copybean: Copybean)(implicit siloScope: SiloScope): String = {

    enforceTypes(copybean)

    val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
    val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
    siloScope.beanGitRepo.add(file, json)
    siloScope.indexer.addCopybean(copybean)

    copybean.id
  }

  def find()(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    logger.debug("Finding all copybeans")
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    logger.debug("Finding copybeans")
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds(params)
    fetchCopybeans(copybeanIds)
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetchCopybean(id)
    })
    Future.sequence(futures)
  }

  def store(inputCopybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {

    val copybeanType = inputCopybeanType.generateValDefIds()

    val file = new File(siloScope.typesDir, "/" + copybeanType.id + ".json")
    val json = Json.stringify(implicitly[Writes[CopybeanType]].writes(copybeanType))
    siloScope.typeGitRepo.add(file, json)
    siloScope.indexer.addCopybeanType(copybeanType)
  }

  protected def checkSiloExists()(implicit siloScope: SiloScope) = {
    if (!siloScope.root.exists) {
      throw new SiloNotInitialized(siloScope.thisSiloId)
    }
  }

  protected def fetchCopybeanType(id: String)(implicit siloScope: SiloScope): CopybeanType = {
    checkSiloExists()
    val file = new File(siloScope.typesDir, "/" + id + ".json")

    if (!file.exists()) {
      predefinedCopybeanTypes.predefinedTypes.getOrElse(id, throw new CopybeanTypeNotFound(id))
    } else {
      val json = FileUtils.readFileToString(file)
      implicitly[Reads[CopybeanType]].reads(Json.parse(json)).get
    }
  }

  def cachedFetchCopybeanType(id: String)(implicit siloScope: SiloScope): Future[CopybeanType] = siloScope.typeCache(id) {
    logger.debug("Finding copybean type")
    fetchCopybeanType(id)
  }

  def fetchAllCopybeanTypes()(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    logger.debug("Finding all copybean types")
    checkSiloExists()
    val copybeanTypeIds = siloScope.indexer.findCopybeanTypeIds()
    fetchCopybeanTypes(copybeanTypeIds)
  }

  protected def fetchCopybeanTypes(copybeanTypeIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    val futures = copybeanTypeIds.map(id => {
      cachedFetchCopybeanType(id)
    })
    Future.sequence(futures)
  }

  def findCopybeanTypes(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    logger.debug("Finding copybean types")
    checkSiloExists()
    val copybeanTypeIds = siloScope.indexer.findCopybeanTypeIds(params)
    fetchCopybeanTypes(copybeanTypeIds)
  }

  protected def enforceTypes(copybean: Copybean)(implicit siloScope: SiloScope, ec: ExecutionContext): Unit = {
    val future = copybean.enforcedTypeIds.map { typeId =>
      cachedFetchCopybeanType(typeId).map { copybeanType =>
        copybeanTypeEnforcer.enforceType(copybeanType, copybean)
      }
    }
    val futureSeq = Future.sequence(future)
    Await.result(futureSeq, 5 seconds)
  }

  def update(id: String, anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): Unit = {

    val copybean = new CopybeanImpl(anonCopybean.enforcedTypeIds, anonCopybean.content, id)

    enforceTypes(copybean)

    val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
    val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
    if (!file.exists()) {
      throw new CopybeanNotFound(id)
    }
    siloScope.beanGitRepo.update(file, json)
    siloScope.beanCache.remove(id)

    siloScope.indexer.updateCopybean(copybean)
  }

  def update(inputCopybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {

    val copybeanType = inputCopybeanType.generateValDefIds()

    val file = new File(siloScope.typesDir, "/" + copybeanType.id + ".json")
    val json = Json.stringify(implicitly[Writes[CopybeanType]].writes(copybeanType))
    if (!file.exists()) {
      throw new CopybeanTypeNotFound(copybeanType.id)
    }
    siloScope.typeGitRepo.update(file, json)
    siloScope.typeCache.remove(copybeanType.id)

    siloScope.indexer.updateCopybeanType(copybeanType)
  }

}
