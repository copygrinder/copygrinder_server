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
import org.copygrinder.pure.copybean.persistence.model.{TreeBranch, TreeCommit, Namespaces, PersistableObject}

import scala.concurrent.{ExecutionContext, Future}

trait PersistenceSupport extends LazyLogging {

  protected var predefinedCopybeanTypes: PredefinedCopybeanTypes

  def getCommitIdOfActiveHeadOfBranch(branchId: TreeBranch)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[TreeCommit] = {

    val headsFuture = getBranchHeads(branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      //TODO: Implement real active branch head calculation
      val headOpt = heads.headOption
      val id = if (headOpt.isDefined) {
        headOpt.get.id
      } else {
        ""
      }

      TreeCommit(id, branchId.treeId)
    })

    activeHeadFuture
  }

  def getCommitIdOfActiveHeadOfBranches(branchIds: Seq[TreeBranch])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[TreeCommit]] = {
    val futures = branchIds.map { branchId =>
      getCommitIdOfActiveHeadOfBranch(branchId)
    }
    Future.sequence(futures)
  }


  def getBranchHeads(branchId: TreeBranch)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[Commit]] = {
    siloScope.persistor.getBranchHeads(branchId)
  }

  protected def fetchFromCommit[T](ids: Seq[(String, String)], commitIds: Seq[TreeCommit])
   (func: ((String, String), Option[PersistableObject]) => T)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Seq[T]] = {

    val persistableObjsFuture = siloScope.persistor.getByIdsAndCommits(ids, commitIds)

    persistableObjsFuture.map { objs =>

      ids.zipWithIndex.map { case (id, index) =>
        func(id, objs(index))
      }
    }
  }

  def fetchCopybeanTypesFromCommits(ids: Seq[String], commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[CopybeanType]] = {

    fetchFromCommit(ids.map(id => (Namespaces.cbtype, id)), commitIds) {
      case ((namespace, id), dataOpt) =>

        if (dataOpt.isEmpty) {
          throw new CopybeanTypeNotFound(id)
        } else {
          dataOpt.get.cbType
        }
    }
  }

}
