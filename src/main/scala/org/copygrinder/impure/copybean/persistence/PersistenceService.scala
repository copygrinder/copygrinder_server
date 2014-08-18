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

import com.softwaremill.macwire.MacwireMacros._
import org.apache.commons.io.FileUtils
import org.copygrinder.pure.copybean.exception.CopybeanNotFound
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.copygrinder.impure.copybean.CopybeanFactory
import org.copygrinder.impure.copybean.search.Indexer
import org.copygrinder.impure.system.Configuration
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}
import spray.caching.{Cache, LruCache}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PersistenceService {

  protected lazy val config = wire[Configuration]

  protected lazy val hashedFileResolver = wire[HashedFileResolver]

  protected lazy val copybeanFactory = wire[CopybeanFactory]

  protected lazy val indexer = wire[Indexer]

  protected lazy val repoDir = new File(config.copybeanRepoRoot + "/" + config.copybeanDefaultRepo + "/")

  protected lazy val gitRepo = new GitRepo(repoDir)

  protected lazy val cache: Cache[Copybean] = LruCache()

  protected implicit def json4sJacksonFormats: Formats = DefaultFormats

  def fetch(id: String): Copybean = {
    val file = hashedFileResolver.locate(id, "json", repoDir)

    if (!file.exists()) {
      throw new CopybeanNotFound()
    } else {
      val json = FileUtils.readFileToString(file)
      read[Copybean](json)
    }
  }

  def cachedFetch(id: String) = cache(id) {
    fetch(id)
  }

  def store(anonCopybean: AnonymousCopybean): Future[String] = {
    val copybean = copybeanFactory.create(anonCopybean)
    store(copybean).map(_ => copybean.id)
  }

  def store(copybean: Copybean): Future[_] = {
    gitRepo.createIfNonExistant()
    val file = hashedFileResolver.locate(copybean.id, "json", repoDir)
    Future {
      gitRepo.add(file, write(copybean))
    }.zip(Future {
      indexer.addCopybean(copybean)
    })
  }

  def find(field: String, phrase: String): Future[Seq[Copybean]] = {
    val copybeanIds = indexer.findCopybeanIds(field, phrase)
    val futures = copybeanIds.map(id => {
      cachedFetch(id)
    })
    Future.sequence(futures)
  }

}
