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
package org.copygrinder.pure.copybean

import org.copygrinder.pure.copybean.model._

import scala.collection.immutable.ListMap

class CopybeanReifier {

  def reify(copybean: CopybeanImpl, types: Set[CopybeanType]): ReifiedCopybean = {

    val decoratedCopybean = addFileUrls(copybean, types)

    val names = types.map(cbType => {
      if (cbType.instanceNameFormat.isDefined) {
        Option((cbType.id, resolveName(cbType.instanceNameFormat.get, copybean, cbType)))
      } else {
        None
      }
    }).flatten.toMap

    new ReifiedCopybeanImpl(copybean.enforcedTypeIds, decoratedCopybean.content, copybean.id, names)
  }

  protected def resolveName(format: String, copybean: CopybeanImpl, cbType: CopybeanType): String = {
    val variables = """\$(.+?)\$""".r.findAllMatchIn(format)

    variables.foldLeft(format)((result, variable) => {
      val variableString = variable.toString()
      val strippedVariable = variableString.substring(1, variableString.length - 1)

      val newVal = if (strippedVariable.startsWith("content.")) {
        val valueOpt = copybean.content.find(field => field._1 == strippedVariable.replace("content.", ""))
        valueOpt match {
          case Some(value) => value._2.toString
          case _ => "''"
        }
      } else if (strippedVariable == "displayName") {
        cbType.displayName.get
      } else {
        "''"
      }

      result.replace(variableString, newVal)

    })
  }

  protected def addFileUrls(copybean: CopybeanImpl, types: Set[CopybeanType]) = {

    val fields: Set[CopybeanFieldDef] = findImageAndFileFields(types)

    modifyContent(copybean, fields) { (fieldData, field) =>
      fieldData + ("url" -> s"copybeans/${copybean.id}/${field.id}")
    }
  }

  protected def findImageAndFileFields(types: Set[CopybeanType]): Set[CopybeanFieldDef] = {
    val typesWithFields = types.filter(_.fields.isDefined)

    val fields = typesWithFields.flatMap(_.fields.get.filter(field =>
      field.`type` == FieldType.File || field.`type` == FieldType.Image
    ))
    fields
  }

  protected def modifyContent[T <: AnonymousCopybean](copybean: T, fields: Set[CopybeanFieldDef])
   (func: (ListMap[String, String], CopybeanFieldDef) => ListMap[String, String]): T = {
    val newContent = fields.foldLeft(copybean.content) { (result, field) =>
      val fieldId = field.id
      val fieldDataOpt = result.get(fieldId)
      if (fieldDataOpt.isDefined) {
        val fieldData = fieldDataOpt.get.asInstanceOf[ListMap[String, String]]
        val newFieldData = func(fieldData, field)
        result.updated(fieldId, newFieldData)
      } else {
        result
      }
    }

    copybean.copyAnonymousCopybean(content = newContent).asInstanceOf[T]
  }

  def unreify[T <: AnonymousCopybean](copybean: T, types: Set[CopybeanType]): T = {

    val fields: Set[CopybeanFieldDef] = findImageAndFileFields(types)

    modifyContent[T](copybean, fields) { (fieldData, field) =>
      fieldData - "url"
    }

  }

}
