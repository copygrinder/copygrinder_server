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
import org.copygrinder.pure.copybean.persistence.model.{PersistableObject, Query, NewCommit, CommitData}

import scala.concurrent.Future

trait VersionedDataPersistor {

  def initSilo(): Future[Unit]

  def getByIdsAndCommit(treeId: String, ids: Seq[(String, String)], commitId: String):
  Future[Seq[Option[PersistableObject]]]

  def getHistoryByIdAndCommit(treeId: String, id: (String, String), commitId: String, limit: Int): Future[Seq[Commit]]

  def getBranchHeads(treeId: String, branchId: String): Future[Seq[Commit]]

  def getCommitsByBranch(treeId: String, branchId: String, limit: Int): Future[Seq[Commit]]

  def commit(commit: NewCommit, data: Seq[CommitData]): Future[Commit]

  def query(treeId: String, commitId: String, limit: Int, query: Query): Future[Seq[PersistableObject]]

}
