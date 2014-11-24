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

import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TypePersistenceService(_predefinedCopybeanTypes: PredefinedCopybeanTypes) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

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
    if (params.nonEmpty) {
      val copybeanTypeIds = siloScope.indexer.findCopybeanTypeIds(params)
      fetchCopybeanTypes(copybeanTypeIds)
    } else {
      fetchAllCopybeanTypes()
    }
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

  def store(inputCopybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {

    val copybeanType = inputCopybeanType.generateValDefIds()

    val file = new File(siloScope.typesDir, "/" + copybeanType.id + ".json")
    val json = Json.stringify(implicitly[Writes[CopybeanType]].writes(copybeanType))
    siloScope.typeGitRepo.add(file, json)
    siloScope.indexer.addCopybeanType(copybeanType)
  }

}
