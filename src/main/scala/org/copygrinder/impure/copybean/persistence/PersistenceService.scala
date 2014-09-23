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

import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.system.{Configuration, SiloScope}
import org.copygrinder.pure.copybean.exception.{CopybeanNotFound, CopybeanTypeNotFound, SiloNotInitialized}
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean, CopybeanType, FieldType}
import org.copygrinder.pure.copybean.persistence.CopybeanTypeEnforcer
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Future}

class PersistenceService(
  config: Configuration,
  hashedFileResolver: HashedFileResolver,
  copybeanFactory: CopybeanFactory,
  copybeanTypeEnforcer: CopybeanTypeEnforcer
  ) extends LazyLogging {

  implicit def json4sJacksonFormats: Formats = DefaultFormats + new EnumNameSerializer(FieldType)

  def fetchCopybean(id: String)(implicit siloScope: SiloScope): Copybean = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)

    if (!file.exists()) {
      throw new CopybeanNotFound(id)
    } else {
      val json = FileUtils.readFileToString(file)
      read[Copybean](json)
    }
  }

  def cachedFetchCopybean(id: String)(implicit siloScope: SiloScope): Future[Copybean] = siloScope.beanCache(id) {
    fetchCopybean(id)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): String = {
    val copybean = copybeanFactory.create(anonCopybean)
    store(copybean)
  }

  def store(copybean: Copybean)(implicit siloScope: SiloScope): String = {

    enforceTypes(copybean)

    val f = Future {
      val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
      siloScope.beanGitRepo.add(file, write(copybean))
    }.zip(Future {
      siloScope.indexer.addCopybean(copybean)
    })

    Await.ready(f, 5 seconds)

    copybean.id
  }

  def find()(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    checkSiloExists()
    val copybeanIds = siloScope.indexer.findCopybeanIds(params)
    fetchCopybeans(copybeanIds)
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[Copybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetchCopybean(id)
    })
    Future.sequence(futures)
  }

  def store(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {
    val f = Future {
      val file = new File(siloScope.typesDir, "/" + copybeanType.id + ".json")
      siloScope.typeGitRepo.add(file, write(copybeanType))
    }.zip(Future {
      siloScope.indexer.addCopybeanType(copybeanType)
    })

    Await.ready(f, 5 seconds)
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
      throw new CopybeanTypeNotFound(id)
    } else {
      val json = FileUtils.readFileToString(file)
      read[CopybeanType](json)
    }
  }

  def cachedFetchCopybeanType(id: String)(implicit siloScope: SiloScope): Future[CopybeanType] = siloScope.typeCache(id) {
    fetchCopybeanType(id)
  }

  def fetchAllCopybeanTypes()(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    checkSiloExists()
    val copybeanTypeIds = siloScope.indexer.findCopybeanTypeIds()
    fetchCopybeanTypes(copybeanTypeIds)
  }

  def fetchCopybeanTypes(copybeanTypeIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    val futures = copybeanTypeIds.map(id => {
      cachedFetchCopybeanType(id)
    })
    Future.sequence(futures)
  }

  def findCopybeanTypes(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
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

}
