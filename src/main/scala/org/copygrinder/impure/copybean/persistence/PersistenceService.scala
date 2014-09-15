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

import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.system.{Configuration, Scoped}
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean, CopybeanType}
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersistenceService(
  config: Configuration, hashedFileResolver: HashedFileResolver, copybeanFactory: CopybeanFactory) {

  protected implicit def json4sJacksonFormats: Formats = DefaultFormats

  def fetch(id: String)(implicit scoped: Scoped): Copybean = {
    val file = hashedFileResolver.locate(id, "json", scoped.beanDir)

    if (!file.exists()) {
      throw new CopybeanNotFound()
    } else {
      val json = FileUtils.readFileToString(file)
      read[Copybean](json)
    }
  }

  def cachedFetch(id: String)(implicit scoped: Scoped): Future[Copybean] = scoped.beanCache(id) {
    fetch(id)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit scoped: Scoped): Future[String] = {
    val copybean = copybeanFactory.create(anonCopybean)
    store(copybean).map(_ => copybean.id)
  }

  def store(copybean: Copybean)(implicit scoped: Scoped): Future[_] = {
    Future {
      val file = hashedFileResolver.locate(copybean.id, "json", scoped.beanDir)
      scoped.beanGitRepo.add(file, write(copybean))
    }.zip(Future {
      scoped.indexer.addCopybean(copybean)
    })
  }

  def find()(implicit scoped: Scoped): Future[Seq[Copybean]] = {
    val copybeanIds = scoped.indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)])(implicit scoped: Scoped): Future[Seq[Copybean]] = {
    val copybeanIds = scoped.indexer.findCopybeanIds(params)
    fetchCopybeans(copybeanIds)
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])(implicit scoped: Scoped): Future[Seq[Copybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetch(id)
    })
    Future.sequence(futures)
  }

  def store(copybeanType: CopybeanType)(implicit scoped: Scoped): Future[(Unit, Unit)] = {
    Future {
      val file = new File(scoped.typesDir, "/" + copybeanType.id + ".json")
      scoped.typeGitRepo.add(file, write(copybeanType))
    }.zip(Future {
      scoped.indexer.addType(copybeanType)
    })
  }

}
