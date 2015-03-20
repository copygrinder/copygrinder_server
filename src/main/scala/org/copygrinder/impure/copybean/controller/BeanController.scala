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
import org.copygrinder.pure.copybean.model.{ReifiedCopybean, AnonymousCopybean, Copybean}
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

    val filteredJsValue = validateAndFilterFields(includedFields, Json.toJson(beans),
      copybeansReservedWords).as[JsArray]

    expandRefs(filteredJsValue, beans, filteredExpandFields)
  }

  protected def expandRefs(filteredJsValue: JsArray, beans: Seq[ReifiedCopybean], expandFields: Set[String])
   (implicit siloScope: SiloScope): JsArray = {
    filteredJsValue.value.zipWithIndex.foldLeft(filteredJsValue)((resultArray, jsValueAndIndex) => {
      val (jsValue, index) = jsValueAndIndex
      val fieldToBeanMap = persistenceService.findExpandableBeans(beans(index), expandFields)
      fieldToBeanMap.foldLeft(resultArray)((resultArray, fieldToBean) => {
        val resultObj = jsValue.as[JsObject]
        val (fieldId, bean) = fieldToBean
        val contentObj = resultObj.\("content").as[JsObject]

        val refOrArray = parseField[JsValue](fieldId) { fieldId =>
          val refObj = contentObj.\(fieldId).as[JsObject]
          refObj +("expand", Json.toJson(bean))
        } { (fieldId, index) =>
          val refArray = contentObj.\(fieldId).as[JsArray]
          val newRefObj = refArray.value(index).as[JsObject] +("expand", Json.toJson(bean))
          JsArray(refArray.value.updated(index, newRefObj))
        }

        val newContentObj = JsObject(contentObj.value.updated(fieldId, refOrArray).toSeq)
        val newBeanFields = resultObj.value.updated("content", newContentObj)
        JsArray(resultArray.value.updated(index, JsObject(newBeanFields.toSeq)))
      })
    })
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
