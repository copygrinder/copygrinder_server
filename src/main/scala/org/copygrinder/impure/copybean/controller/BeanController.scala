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
import org.copygrinder.pure.copybean.exception.{JsonInputException, UnknownQueryParameter}
import org.copygrinder.pure.copybean.model.ReifiedField.{ListReifiedField, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json._
import scala.concurrent.duration._

import scala.concurrent.Await

class BeanController(persistenceService: CopybeanPersistenceService) extends JsonReads with JsonWrites with ControllerSupport {

  def cachedFetchCopybean(id: String)(implicit siloScope: SiloScope): JsValue = {
    val future = persistenceService.cachedFetchCopybean(id)
    Json.toJson(future)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): JsString = {
    val bean = persistenceService.store(anonCopybean)
    JsString(bean.id)
  }

  def store(copybean: Copybean)(implicit siloScope: SiloScope): JsString = {
    val id = persistenceService.store(copybean)
    JsString(id)
  }

  protected val copybeansReservedWords = Set("enforcedTypeIds", "id", "content", "type", "names")

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val (includedFields, nonFieldParams) = partitionIncludedFields(params)

    val (expandFields, regularFields) = partitionFields(nonFieldParams, "expand")

    val filteredExpandFields = if (includedFields.nonEmpty) {
      expandFields.map(expandField => {
        if (includedFields.exists(expandField.startsWith(_))) {
          Some(expandField)
        } else if (expandField == "*") {
          Some(expandField)
        } else {
          None
        }
      }).flatten
    } else {
      expandFields
    }

    regularFields.foreach(param => {
      if (!copybeansReservedWords.exists(reservedWord => param._1.startsWith(reservedWord))) {
        throw new UnknownQueryParameter(param._1)
      }
    })

    val future = persistenceService.find(regularFields)
    val beans = Await.result(future, 5 seconds)

    val decoratedBeans = decorateExpandRefs(beans, filteredExpandFields)

    validateAndFilterFields(includedFields, Json.toJson(decoratedBeans), copybeansReservedWords).as[JsArray]
  }

  protected def decorateExpandRefs(beans: Seq[ReifiedCopybean], expandFields: Set[String])
   (implicit siloScope: SiloScope): Seq[ReifiedCopybean] = {

    val fieldToBeanMap = persistenceService.findExpandableBeans(beans, expandFields)

    beans.map(bean => {
      val newFields = bean.fields.map(field => {
        val decoratedField = decorateField(field._2, fieldToBeanMap)
        (field._1, decoratedField)
      })
      new ReifiedCopybeanImpl(bean.enforcedTypeIds, bean.content, bean.id, bean.names, bean.types) {
        override lazy val fields = newFields
      }
    })
  }

  protected def decorateField(field: ReifiedField, fieldToBeanMap: Map[String, ReifiedCopybean]): ReifiedField = {
    field match {
      case r: ReifiedField with ReferenceReifiedField => {
        fieldToBeanMap.get(r.fieldDef.id).fold(r) { mapRefBean =>
          new ReifiedField(r.fieldDef, r.value, r.parent) with ReferenceReifiedField {
            override val refBean = Some(mapRefBean)
          }
        }
      }
      case list: ReifiedField with ListReifiedField => {
        val newSeq = list.castVal.map(seqField => {
          decorateField(seqField, fieldToBeanMap)
        })
        new ReifiedField(list.fieldDef, list.value, list.parent) with ListReifiedField {
          override lazy val castVal = newSeq
        }
      }
      case r: ReifiedField => r
    }
  }

  def update(id: String, anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): JsValue = {
    persistenceService.update(id, anonCopybean)
    JsNull
  }

  def delete(id: String)(implicit siloScope: SiloScope): JsValue = {
    persistenceService.delete(id)
    JsNull
  }

  def createSilo()(implicit siloScope: SiloScope): JsValue = {
    persistenceService.createSilo()
    JsNull
  }

}
