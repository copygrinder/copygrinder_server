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
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.{CopybeanTypeNotFound, SiloNotInitialized}
import org.copygrinder.pure.copybean.model.CopybeanType
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites, PredefinedCopybeanTypes}
import play.api.libs.json.{Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceSupport extends LazyLogging with JsonReads with JsonWrites {

  protected var predefinedCopybeanTypes: PredefinedCopybeanTypes

  protected def fetchCopybeanType(id: String)(implicit siloScope: SiloScope, ex: ExecutionContext): CopybeanType = {
    checkSiloExists()
    val file = new File(siloScope.typesDir, "/" + id + ".json")

    if (!file.exists()) {
      predefinedCopybeanTypes.predefinedTypes.getOrElse(id, throw new CopybeanTypeNotFound(id))
    } else {
      val json = FileUtils.readFileToString(file)
      implicitly[Reads[CopybeanType]].reads(Json.parse(json)).get
    }
  }

  def cachedFetchCopybeanType(id: String)(implicit siloScope: SiloScope, ex: ExecutionContext): Future[CopybeanType] = siloScope.typeCache(id) {
    fetchCopybeanType(id)
  }

  protected def checkSiloExists()(implicit siloScope: SiloScope) = {
    if (!siloScope.root.exists) {
      throw new SiloNotInitialized(siloScope.siloId)
    }
  }

}
