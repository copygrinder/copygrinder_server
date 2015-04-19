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

import org.copygrinder.impure.copybean.persistence.CopybeanPersistenceService
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.UnknownQueryParameter
import org.copygrinder.pure.copybean.model.ReifiedField.{ListReifiedField, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{NewCommit, JsonReads, JsonWrites}
import play.api.libs.json._

import scala.concurrent.Future

class BeanController(persistenceService: CopybeanPersistenceService) extends JsonReads with JsonWrites with ControllerSupport {

  def fetchCopybean(id: String)(implicit siloScope: SiloScope): JsValue = {
    val future = persistenceService.fetchCopybeansFromBranchHead(Seq(id), siloScope.defaultBranch)
    Json.toJson(future)
  }

  def store(anonCopybean: AnonymousCopybean, branchId: String, parentCommitId: String)
   (implicit siloScope: SiloScope): JsString = {
    val commit = new NewCommit(branchId, parentCommitId, "", "")
    val beanFuture = persistenceService.storeAnonBean(anonCopybean, commit)
    Json.toJson(beanFuture).as[JsString]
  }

  protected val copybeansReservedWords = Set("enforcedTypeIds", "id", "content", "type", "names")

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val (includedFields, nonFieldParams) = partitionIncludedFields(params)

    val (expandFields, regularFields) = partitionFields(nonFieldParams, "expand")

    val filteredExpandFields = if (includedFields.nonEmpty) {
      expandFields.flatMap(expandField => {
        if (includedFields.exists(expandField.startsWith(_))) {
          Some(expandField)
        } else if (expandField == "*") {
          Some(expandField)
        } else {
          None
        }
      })
    } else {
      expandFields
    }

    regularFields.foreach(param => {
      if (!copybeansReservedWords.exists(reservedWord => param._1.startsWith(reservedWord))) {
        throw new UnknownQueryParameter(param._1)
      }
    })

    val headFuture = persistenceService.getCommitIdOfActiveHeadOfBranch(siloScope.defaultBranch)

    val decoratedJsonFuture = headFuture.flatMap(head => {

      val beansFuture = persistenceService.findByCommit(head, regularFields)

      beansFuture.map(beans => {
        val decoratedBeans = decorateExpandRefs(beans, filteredExpandFields, head)
        validateAndFilterFields(includedFields, Json.toJson(decoratedBeans), copybeansReservedWords).as[JsArray]
      })

    })

    Json.toJson(decoratedJsonFuture)

  }

  protected def decorateExpandRefs(beans: Seq[ReifiedCopybean], expandFields: Set[String], commitId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    val fieldToBeanMapFuture = persistenceService.findExpandableBeans(beans, expandFields, commitId)

    fieldToBeanMapFuture.map(fieldToBeanMap => {

      beans.map(bean => {
        val newFields = bean.fields.map(field => {
          val decoratedField = decorateField(field._2, fieldToBeanMap)
          (field._1, decoratedField)
        })
        new ReifiedCopybeanImpl(bean.enforcedTypeIds, bean.content, bean.id, bean.types) {
          override lazy val fields = newFields
        }
      })

    })

  }

  protected def decorateField(field: ReifiedField, fieldToBeanMap: Map[String, ReifiedCopybean]): ReifiedField = {
    field match {
      case r: ReifiedField with ReferenceReifiedField =>
        fieldToBeanMap.get(r.fieldDef.id).fold(r) { mapRefBean =>
          new ReifiedField(r.fieldDef, r.value, r.parent) with ReferenceReifiedField {
            override val refBean = Some(mapRefBean)
          }
        }
      case list: ReifiedField with ListReifiedField =>
        val newSeq = list.castVal.map(seqField => {
          decorateField(seqField, fieldToBeanMap)
        })
        new ReifiedField(list.fieldDef, list.value, list.parent) with ListReifiedField {
          override lazy val castVal = newSeq
        }
      case r: ReifiedField => r
    }
  }

  def update(id: String, anonCopybean: AnonymousCopybean, branchId: String, parentCommitId: String)
   (implicit siloScope: SiloScope): JsValue = {
    val commit = new NewCommit(branchId, parentCommitId, "", "")
    val newCommitId = persistenceService.update(id, anonCopybean, commit)
    Json.toJson(newCommitId)
  }

  def delete(id: String)(implicit siloScope: SiloScope, branchId: String, parentCommitId: String): JsValue = {
    val commit = new NewCommit(branchId, parentCommitId, "", "")
    val newCommitId = persistenceService.delete(id, commit)
    Json.toJson(newCommitId)
  }

  def createSilo()(implicit siloScope: SiloScope): JsValue = {
    persistenceService.createSilo()
    JsNull
  }

}
