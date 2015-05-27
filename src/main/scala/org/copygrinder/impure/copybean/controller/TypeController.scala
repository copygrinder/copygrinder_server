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
package org.copygrinder.impure.copybean.controller

import org.copygrinder.impure.copybean.persistence.TypePersistenceService
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.model.{Commit, CopybeanType}
import org.copygrinder.pure.copybean.persistence.model.CommitRequest
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json._

import scala.concurrent.{ExecutionContext, Future}

class TypeController(persistenceService: TypePersistenceService)
 extends JsonReads with JsonWrites with ControllerSupport {

  def fetchCopybeanType(id: String, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {

    val branchIds = getBranchIds(params)

    val future = persistenceService.getCommitIdOfActiveHeadOfBranches(branchIds).flatMap(heads => {
      persistenceService.fetchCopybeanTypesFromCommits(Seq(id), heads)
    })
    Json.toJson(future.map(_.head))
  }

  def findCopybeanTypes(params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {

    val branchIds = getBranchIds(params)

    val fields = getParams(params, "fields")


    val future = persistenceService.getCommitIdOfActiveHeadOfBranches(branchIds).flatMap(heads => {
      persistenceService.findCopybeanTypes(heads, params)
    })
    validateAndFilterFields(fields, Json.toJson(future), copybeanTypeReservedWords)
  }


  def update(copybeanType: CopybeanType, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    doCommit(params) { commit =>
      persistenceService.update(copybeanType, commit)
    }
  }

  def store(copybeanTypes: Seq[CopybeanType], params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    doCommit(params) { commit =>
      persistenceService.store(copybeanTypes, commit)
    }
  }

  def delete(id: String, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    doCommit(params) { commit =>
      persistenceService.delete(id, commit)
    }
  }

  protected def doCommit(params: Map[String, List[String]])(func: (CommitRequest) => Future[Commit])
   (implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    val branchId = getBranchId(params)
    val parentCommitId = getParentCommitId(params)

    val commit = new CommitRequest(branchId, parentCommitId, "", "", None)
    val future = func(commit).map(_.id)

    Json.toJson(future)
  }

  def getHistoryById(id: String, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ec: ExecutionContext): JsValue = {

    val branchIds = getBranchIds(params)

    val future = persistenceService.getCommitIdOfActiveHeadOfBranches(branchIds).map { heads =>
      persistenceService.getHistoryByIdAndCommits(id, heads)
    }

    Json.toJson(future)
  }

  protected val copybeanTypeReservedWords = Set(
    "id", "displayName", "instanceNameFormat", "instanceNameFormat", "fields", "validators", "cardinality"
  )

}
