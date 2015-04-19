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

import org.copygrinder.pure.copybean.model.Commit
import org.copygrinder.pure.copybean.persistence.{IndexData, NewCommit, Query}

import scala.concurrent.Future

trait VersionedDataPersistor {

  def initSilo(): Future[Unit]

  def addToIndex(index: IndexData): Future[Unit]

  def getByIdsAndCommit(namespace: String, ids: Seq[String], commitId: String): Future[Seq[Option[String]]]

  def getHistoryByIdAndCommit(namespace: String, id: String, commitId: String, limit: Int): Future[Seq[Commit]]

  def getBranchHeads(branchId: String): Future[Seq[String]]

  def getCommitsByBranch(branchId: String, limit: Int): Future[Seq[Commit]]

  def commit(commit: NewCommit, data: Map[(String, String), Option[String]], index: IndexData): Future[String]

  def findAll(commitId: String, limit: Int, namespace: String): Future[Seq[String]]

  def query(commitId: String, limit: Int, namespace: String, query: Query): Future[Seq[String]]

}
