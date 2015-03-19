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

import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.ReifiedField.ListReifiedField
import org.copygrinder.pure.copybean.model.{ReifiedField, ReifiedCopybeanImpl}
import play.api.libs.json.{JsArray, JsObject, JsUndefined, JsValue}

import scala.collection.Seq


trait ControllerSupport {

  protected def partitionFields(params: Seq[(String, String)], key: String) = {
    val (matchingParams, nonMatchingParams) = params.partition(_._1 == key)
    val flatFields = matchingParams.flatMap(_._2.split(',')).toSet
    (flatFields, nonMatchingParams)
  }

  protected def partitionIncludedFields(params: Seq[(String, String)]) = {
    partitionFields(params, "fields")
  }

  protected def validateAndFilterFields(keepFields: Set[String], jsValue: JsValue, allowedWords: Set[String]) = {
    if (keepFields.nonEmpty) {
      val (validFields, invalidFields) = keepFields.partition(field => {
        allowedWords.exists(word => {
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

  protected def parseField[T](field: String)(singleFunc: (String) => T)
   (arrayFunc: (String, Int) => T): T = {
    if (field.endsWith(")")) {
      val fieldId = field.takeWhile(_ != '(')
      val index = field.dropWhile(_ != '(').drop(1).takeWhile(_ != ')').toInt
      arrayFunc(fieldId, index)
    } else {
      singleFunc(field)
    }
  }

}
