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
package org.copygrinder.impure.copybean.persistence.backend

import org.copygrinder.pure.copybean.model.Commit
import org.copygrinder.pure.copybean.persistence.model._

import scala.concurrent.{ExecutionContext, Future}

trait VersionedDataPersistor {

  def initSilo()(implicit ec: ExecutionContext): Future[Unit]

  def getByIdsAndCommits(ids: Seq[(String, String)], commitIds: Seq[TreeCommit])
   (implicit ec: ExecutionContext): Future[Seq[Option[(PersistableObject, Commit)]]]

  def getHistoryByIdAndCommits(id: (String, String), commitIds: Seq[TreeCommit], limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]]

  def getBranches(treeIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[TreeBranch]]

  def getBranchHeads(branchId: TreeBranch)
   (implicit ec: ExecutionContext): Future[Seq[Commit]]

  def getCommitsByBranch(branchId: TreeBranch, limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]]

  def commit(commit: CommitRequest, datas: Seq[CommitData])
   (implicit ec: ExecutionContext): Future[Commit]

  def query(commitIds: Seq[TreeCommit], limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[PersistableObject]]

}
