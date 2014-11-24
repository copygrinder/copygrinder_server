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
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.CopybeanType
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json._

import scala.collection.Seq
import scala.concurrent.ExecutionContext

class TypeController(persistenceService: TypePersistenceService) extends JsonReads with JsonWrites {

  def fetchAllCopybeanTypes()(implicit siloScope: SiloScope): JsValue = {
    val futures = persistenceService.fetchAllCopybeanTypes()
    Json.toJson(futures)
  }

  def findCopybeanTypes(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val (fields, nonFieldParams) = extractFields(params)
    val futures = persistenceService.findCopybeanTypes(nonFieldParams)
    keepFields(fields, Json.toJson(futures))
  }


  def update(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {
    persistenceService.update(copybeanType)
  }

  def store(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {
    persistenceService.store(copybeanType)
  }

  def fetchCopybeanType(id: String)(implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    val future = persistenceService.cachedFetchCopybeanType(id)
    Json.toJson(future)
  }


  protected def extractFields(params: Seq[(String, String)]) = {
    val (fields, nonFieldParams) = params.partition(_._1 == "fields")
    val flatFields = fields.flatMap(_._2.split(',')).toSet
    (flatFields, nonFieldParams)
  }

  protected val copybeanTypeReservedWords = Set("id", "displayName", "instanceNameFormat", "instanceNameFormat", "fields", "validators", "cardinality")

  protected def keepFields(fields: Set[String], jsValue: JsValue) = {
    if (fields.nonEmpty) {
      val (validFields, invalidFields) = fields.partition(field => {
        copybeanTypeReservedWords.exists(word => {
          field == word
        })
      })

      if (invalidFields.nonEmpty) {
        throw new JsonInputException("One or more fields are invalid: " + invalidFields.mkString(","))
      }

      filterFields(validFields, jsValue)
    } else {
      jsValue
    }
  }

  protected def filterFields(keepFields: Set[String], jsValue: JsValue): JsValue = {
    jsValue match {
      case obj: JsObject =>
        filterObject(keepFields, obj)
      case array: JsArray =>
        filterArray(keepFields, array)
      case other => other
    }
  }

  def filterObject(keepFields: Set[String], obj: JsObject): JsObject = {
    val fields = keepFields.foldLeft(Seq[(String, JsValue)]())((result, field) => {
      val value = obj.\(field)
      value match {
        case _: JsUndefined => result
        case _ => result :+(field, value)
      }
    })
    JsObject(fields)
  }

  def filterArray(keepFields: Set[String], array: JsArray): JsArray = {
    val filteredFields = array.value.map(a => filterFields(keepFields, a))
    val emptiesRemoved = filteredFields.filter(field => {
      val obj = field.asOpt[JsObject]
      if (obj.isDefined) {
        obj.get.fields.nonEmpty
      } else {
        true
      }
    })
    JsArray(emptiesRemoved)
  }
}
