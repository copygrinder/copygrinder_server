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
import org.copygrinder.pure.copybean.model.{CopybeanImpl, ReifiedCopybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites, PredefinedCopybeanTypes}
import play.api.libs.json.{Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceSupport extends LazyLogging with JsonReads with JsonWrites {

  protected var predefinedCopybeanTypes: PredefinedCopybeanTypes

  protected def fetchFromCommit[T](ids: Seq[String], commitId: String, namespace: String)
   (func: (String, Option[String]) => T)(implicit siloScope: SiloScope): Future[Seq[T]] = {

    val dataStringsFuture = siloScope.persistor.getByIdsAndCommit(namespace, ids, commitId)

    dataStringsFuture.map(dataStrings => {
      ids.zipWithIndex.map { case (id, index) =>
        func(id, dataStrings(index))
      }
    })

  }

  protected def fetchCopybeanTypesFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[CopybeanType]] = {

    fetchFromCommit(ids, commitId, "type") { case (id, dataOpt) =>

      if (dataOpt.isEmpty) {
        predefinedCopybeanTypes.predefinedTypes.getOrElse(id, throw new CopybeanTypeNotFound(id))
      } else {
        val json = dataOpt.get
        implicitly[Reads[CopybeanType]].reads(Json.parse(json)).get
      }
    }

  }

}
