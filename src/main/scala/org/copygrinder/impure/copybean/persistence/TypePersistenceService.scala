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
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.persistence.model.{Namespaces, NewCommit, Query, Trees}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future


class TypePersistenceService(
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 indexer: Indexer
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  def findCopybeanTypes(commitId: String, params: Map[String, Seq[String]])
   (implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    val query = new Query(params.map(v => (Namespaces.cbtype, v._1) -> v._2))
    siloScope.persistor.query(Trees.userdata, commitId, 100, query).map(objs => {
      objs.map(_.cbType)
    })
  }

  def update(copybeanType: CopybeanType, commit: NewCommit)(implicit siloScope: SiloScope): Future[Commit] = {
    val existingTypeFuture = getExistingType(copybeanType.id, commit.parentCommitId)

    existingTypeFuture.flatMap(oldType => {
      val data = indexer.indexUpdateType(oldType, copybeanType)
      siloScope.persistor.commit(commit, Seq(data))
    })
  }

  protected def getExistingType(id: String, commit: String)(implicit siloScope: SiloScope) = {
    val existingTypeFuture = siloScope.persistor.getByIdsAndCommit(
      Trees.userdata, Seq((Namespaces.cbtype, id)), commit
    )

    existingTypeFuture.map(seqOpt => {
      if (seqOpt.isEmpty || seqOpt.head.isEmpty) {
        throw new CopybeanTypeNotFound(id)
      } else {
        seqOpt.head.get.cbType
      }
    })
  }

  def store(copybeanType: CopybeanType, commit: NewCommit)(implicit siloScope: SiloScope): Future[Commit] = {
    val data = indexer.indexAddType(copybeanType)
    siloScope.persistor.commit(commit, Seq(data))
  }

  def delete(id: String, commit: NewCommit)(implicit siloScope: SiloScope): Future[Commit] = {
    getExistingType(id, commit.parentCommitId).flatMap(copybeanType => {
      val data = indexer.indexDeleteType(copybeanType)
      siloScope.persistor.commit(commit, Seq(data))
    })
  }

}
