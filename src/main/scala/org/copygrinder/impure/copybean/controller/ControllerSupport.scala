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

import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.{MissingParameter, JsonInputException}
import play.api.libs.json.{JsArray, JsObject, JsUndefined, JsValue}

import scala.collection.immutable.ListMap


trait ControllerSupport {

  protected def partitionFields(params: Map[String, List[String]], key: String) = {

    val matchingParams = params.getOrElse(key, List()).flatMap(param => {
      param.split(',')
    })
    val nonMatchingParams = params - key
    (matchingParams, nonMatchingParams)
  }

  protected def partitionIncludedFields(params: Map[String, List[String]]) = {
    partitionFields(params, "fields")
  }

  protected def validateAndFilterFields(keepFields: List[String], jsValue: JsValue, allowedWords: Set[String]) = {
    if (keepFields.nonEmpty) {
      val (validFields, invalidFields) = keepFields.partition(field => {
        allowedWords.exists(word => {
          field == word || field.startsWith(word + ".")
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

  protected def filterFields(keepFields: List[String], jsValue: JsValue): JsValue = {
    jsValue match {
      case obj: JsObject =>
        filterObject(keepFields, obj)
      case array: JsArray =>
        filterArray(keepFields, array)
      case other => other
    }
  }

  def filterObject(keepFields: List[String], obj: JsObject): JsObject = {
    val fields = keepFields.foldLeft(ListMap[String, JsValue]())((result, field) => {
      val (fieldId, value) = if (field.contains('.')) {
        val (prefix, suffixDot) = field.span(_ != '.')
        val suffix = suffixDot.drop(1)
        val nestedJsValue = filterFields(List(suffix), obj.\(prefix))
        (prefix, nestedJsValue)
      } else {
        (field, obj.\(field))
      }
      value match {
        case _: JsUndefined => result
        case a: JsArray if (a.value.isEmpty) => result
        case obj: JsObject if (obj.fields.isEmpty) => result
        case _ =>
          result.updated(fieldId, value)
      }
    })
    JsObject(fields.toSeq)
  }

  def filterArray(keepFields: List[String], array: JsArray): JsArray = {
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

  protected def getBranchId(params: Map[String, List[String]])(implicit siloScope: SiloScope) = {
    val branchOpt = params.get("branch")
    if (branchOpt.isDefined && branchOpt.get.nonEmpty) {
      branchOpt.get.head
    } else {
      siloScope.defaultBranch
    }
  }

  protected def getParentCommitId(params: Map[String, List[String]])(implicit siloScope: SiloScope) = {
    val parentOpt = params.get("parent")
    if (parentOpt.isDefined && parentOpt.get.nonEmpty) {
      parentOpt.get.head
    } else {
      throw new MissingParameter("parent")
    }
  }


}
