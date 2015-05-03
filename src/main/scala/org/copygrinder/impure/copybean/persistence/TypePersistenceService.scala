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
import scala.concurrent.Future


class TypePersistenceService(
 _predefinedCopybeanTypes: PredefinedCopybeanTypes
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  def findCopybeanTypes(commitId: String, params: Map[String, Seq[String]])
   (implicit siloScope: SiloScope): Future[Seq[CopybeanType]] = {
    val query = new Query(params.map(v => (Namespaces.cbtype, v._1) -> v._2))
    siloScope.persistor.query(Trees.userdata, commitId, 100, query).map(objs => {
      objs.map(_.cbType)
    })
  }

  def update(copybeanType: CopybeanType, commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {

    val data = CommitData((Namespaces.cbtype, copybeanType.id), Some(PersistableObject(copybeanType)))
    siloScope.persistor.commit(commit, Seq(data))
  }

  def store(copybeanType: CopybeanType, commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {
    val data = CommitData((Namespaces.cbtype, copybeanType.id), Some(PersistableObject(copybeanType)))
    siloScope.persistor.commit(commit, Seq(data))
  }

  def delete(id: String, commit: CommitRequest)(implicit siloScope: SiloScope): Future[Commit] = {
    val data = CommitData((Namespaces.cbtype, id), None)
    siloScope.persistor.commit(commit, Seq(data))
  }

}
