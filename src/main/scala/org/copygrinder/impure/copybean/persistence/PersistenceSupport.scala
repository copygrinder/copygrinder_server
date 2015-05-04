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
import org.copygrinder.pure.copybean.model.{Commit, CopybeanType}
import org.copygrinder.pure.copybean.persistence.PredefinedCopybeanTypes
import org.copygrinder.pure.copybean.persistence.model.{Namespaces, PersistableObject, Trees}

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceSupport extends LazyLogging {

  protected var predefinedCopybeanTypes: PredefinedCopybeanTypes

  def getCommitIdOfActiveHeadOfBranch(treeId: String, branchId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[String] = {
    //TODO: Implement real active branch head calculation
    val headsFuture = siloScope.persistor.getBranchHeads(treeId, branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      heads.headOption.getOrElse(throw new BranchNotFound(branchId)).id
    })

    activeHeadFuture
  }

  def getBranchHeads(treeId: String, branchId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[Commit]] = {
    siloScope.persistor.getBranchHeads(treeId, branchId)
  }

  protected def fetchFromCommit[T](ids: Seq[(String, String)], commitId: String)
   (func: ((String, String), Option[PersistableObject]) => T)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Seq[T]] = {

    val persistableObjsFuture = siloScope.persistor.getByIdsAndCommit(Trees.userdata, ids, commitId)

    persistableObjsFuture.map(objs => {
      objs.zipWithIndex.map { case (obj, index) =>
        func(ids(index), obj)
      }
    })

  }

  def fetchCopybeanTypesFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[CopybeanType]] = {

    fetchFromCommit(ids.map(id => (Namespaces.cbtype, id)), commitId) { case ((namespace, id), dataOpt) =>

      if (dataOpt.isEmpty) {
        throw new CopybeanTypeNotFound(id)
      } else {
        dataOpt.get.cbType
      }
    }
  }

}
