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

import java.util.UUID

import org.apache.commons.io.FileUtils
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.CopybeanReifier
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class CopybeanPersistenceService(
 hashedFileResolver: HashedFileResolver,
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 copybeanReifier: CopybeanReifier,
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  protected def fetchCopybean(id: String)(implicit siloScope: SiloScope): ReifiedCopybean = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)

    val copybean = if (!file.exists()) {
      predefinedCopybeans.predefinedBeans.getOrElse(id, throw new CopybeanTypeNotFound(id))
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
    if (params.nonEmpty) {
      checkSiloExists()
      val copybeanIds = siloScope.indexer.findCopybeanIds(params)
      fetchCopybeans(copybeanIds)
    } else {
      find()
    }
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetchCopybean(id)
    })
    Future.sequence(futures)
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

  protected def enforceTypes(copybean: Copybean)(implicit siloScope: SiloScope, ec: ExecutionContext): Unit = {
    val future = copybean.enforcedTypeIds.map { typeId =>
      cachedFetchCopybeanType(typeId).map { copybeanType =>
        val validatorBeansMap = fetchValidators(copybeanType)
        copybeanTypeEnforcer.enforceType(copybeanType, copybean, validatorBeansMap)
      }
    }
    val futureSeq = Future.sequence(future)
    Await.result(futureSeq, 5 seconds)
  }

  protected def fetchValidators(copybeanType: CopybeanType)(implicit siloScope: SiloScope, ec: ExecutionContext): Map[String, ReifiedCopybean] = {
    if (copybeanType.validators.isDefined) {
      val validatorTypes = copybeanType.validators.get.map(_.`type`)
      val validatorBeansFuture = Future.sequence(validatorTypes.map(typeId => cachedFetchCopybean(s"validator.$typeId")))
      val validatorBeans = Await.result(validatorBeansFuture, 5 seconds)
      validatorBeans.map(validator => validator.id -> validator).toMap
    } else {
      Map.empty
    }
  }

  def delete(id: String)(implicit siloScope: SiloScope): Unit = {
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)
    siloScope.beanGitRepo.delete(file)
    siloScope.indexer.deleteCopybean(id)
    siloScope.beanCache.remove(id)
  }


}
