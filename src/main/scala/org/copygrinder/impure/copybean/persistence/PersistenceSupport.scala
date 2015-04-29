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

import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.{BranchNotFound, CopybeanTypeNotFound}
import org.copygrinder.pure.copybean.model.CopybeanType
import org.copygrinder.pure.copybean.persistence.model.{PersistableObject, Namespaces, Trees}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites, PredefinedCopybeanTypes}
import play.api.libs.json.{Json, Reads}

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceSupport extends LazyLogging with JsonReads with JsonWrites {

  protected var predefinedCopybeanTypes: PredefinedCopybeanTypes

  def getCommitIdOfActiveHeadOfBranch(branchId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[String] = {
    //TODO: Implement real active branch head calculation
    val headsFuture = siloScope.persistor.getBranchHeads("content", branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      heads.headOption.getOrElse(throw new BranchNotFound(branchId)).id
    })

    activeHeadFuture
  }

  protected def fetchFromCommit[T](ids: Seq[(String, String)], commitId: String)
   (func: ((String, String), Option[PersistableObject]) => T)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Seq[T]] = {

    val dataStringsFuture = siloScope.persistor.getByIdsAndCommit(Trees.userdata, ids, commitId)

    dataStringsFuture.map(dataStrings => {
      dataStrings.zipWithIndex.map { case (obj, index) =>
        func(ids(index), obj)
      }
    })

  }

  def fetchCopybeanTypesFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[CopybeanType]] = {

    fetchFromCommit(ids.map(id => (Namespaces.cbtype, id)), commitId) { case ((namespace, id), dataOpt) =>

      if (dataOpt.isEmpty) {
        predefinedCopybeanTypes.predefinedTypes.getOrElse(id, throw new CopybeanTypeNotFound(id))
      } else {
        dataOpt.get.cbType
      }
    }
  }

}
