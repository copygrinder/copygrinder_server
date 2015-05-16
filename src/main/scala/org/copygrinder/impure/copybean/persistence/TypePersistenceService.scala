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

import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.persistence.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


class TypePersistenceService(
 _predefinedCopybeanTypes: PredefinedCopybeanTypes
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  def findCopybeanTypes(commitIds: Seq[TreeCommit], params: Map[String, Seq[String]])
   (implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    val query = new Query(params.map(v => (Namespaces.cbtype, v._1) -> v._2), Some(Namespaces.cbtype))
    siloScope.persistor.query(commitIds, siloScope.defaultLimit, query).map(objs => {
      objs.map(_.cbType)
    })
  }

  def update(copybeanType: CopybeanType, commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {

    val data = CommitData((Namespaces.cbtype, copybeanType.id), Some(PersistableObject(copybeanType)))
    siloScope.persistor.commit(commit, Seq(data))
  }

  def store(copybeanTypes: Seq[CopybeanType], commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {
    val data = copybeanTypes.map(copybeanType => {
      CommitData((Namespaces.cbtype, copybeanType.id), Some(PersistableObject(copybeanType)))
    })
    siloScope.persistor.commit(commit, data)
  }

  def delete(id: String, commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {
    val data = CommitData((Namespaces.cbtype, id), None)
    siloScope.persistor.commit(commit, Seq(data))
  }

  def getHistoryByIdAndCommits(id: String, commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[Commit]] = {
    siloScope.persistor.getHistoryByIdAndCommits((Namespaces.cbtype, id), commitIds, siloScope.defaultLimit)
  }

}
