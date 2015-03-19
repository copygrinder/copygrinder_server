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
import org.copygrinder.pure.copybean.persistence.UntypedCaster

import scala.collection.immutable.ListMap

class CopybeanReifier {

  protected val caster = new UntypedCaster()

  def reify(copybean: Copybean, types: Set[CopybeanType]): ReifiedCopybeanImpl = {
    new ReifiedCopybeanImpl(copybean.enforcedTypeIds, copybean.content, copybean.id, Map(), types)
  }

  def decorate(copybean: Copybean, types: Set[CopybeanType]): ReifiedCopybeanImpl = {

    val decoratedCopybean = addFileUrls(copybean, types)

    val names = types.map(cbType => {
      if (cbType.instanceNameFormat.isDefined) {
        Option((cbType.id, resolveName(cbType.instanceNameFormat.get, copybean, cbType)))
      } else {
        None
      }
    }).flatten.toMap

    new ReifiedCopybeanImpl(copybean.enforcedTypeIds, decoratedCopybean.content, copybean.id, names, types)
  }

  protected def resolveName(format: String, copybean: Copybean, cbType: CopybeanType): String = {
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

  protected def addFileUrls(copybean: Copybean, types: Set[CopybeanType]) = {

    val imageAndFilefields: Set[CopybeanFieldDef] = findImageAndFileFields(types)

    def addUrl(fieldData: Any, copybean: Copybean, fieldId: String): Map[String, String] = {
      val fieldMap = caster.anyToMapThen(fieldData, s"bean ${copybean.id}", s"Field $fieldId") {
        caster.mapToMapStringString
      }
      fieldMap + ("url" -> s"copybeans/${copybean.id}/$fieldId")
    }

    val decoratedCopybean = modifyContent(copybean, imageAndFilefields) { (fieldData, field) =>
      addUrl(fieldData, copybean, field.id)
    }

    val listFields = findListFieldsWithFileOrImage(types)

    modifyContent(decoratedCopybean, listFields) { (fieldData, field) =>
      caster.anyToSeqThen(fieldData, s"bean ${copybean.id}", s"Field ${field.id}") { (seq, _, _) =>
        seq.zipWithIndex.map(valueAndIndex => {
          val (value, index) = valueAndIndex
          addUrl(value, copybean, s"${field.id}($index)")
        })
      }
    }
  }

  protected def findImageAndFileFields(types: Set[CopybeanType]): Set[CopybeanFieldDef] = {
    val typesWithFields = types.filter(_.fields.isDefined)

    val fields = typesWithFields.flatMap(_.fields.get.filter(field =>
      field.`type` == FieldType.File || field.`type` == FieldType.Image
    ))
    fields
  }

  protected def findListFieldsWithFileOrImage(types: Set[CopybeanType]): Set[CopybeanFieldDef] = {
    val typesWithFields = types.filter(_.fields.isDefined)

    val fields = typesWithFields.flatMap(_.fields.get.filter(field =>
      field.`type` == FieldType.List
    )).filter(field => {
      field match {
        case l: ListType => l.listType == "File" || l.listType == "Image"
        case other => false
      }
    })
    fields
  }

  protected def modifyContent[T <: AnonymousCopybean](copybean: T, fields: Set[CopybeanFieldDef])
   (func: (Any, CopybeanFieldDef) => Any): T = {
    val newContent = fields.foldLeft(copybean.content) { (result, field) =>
      val fieldId = field.id
      val fieldDataOpt = result.get(fieldId)
      if (fieldDataOpt.isDefined) {
        val fieldData = fieldDataOpt.get
        val newFieldData = func(fieldData, field)
        result.updated(fieldId, newFieldData)
      } else {
        result
      }
    }

    copybean.copyAnonymousCopybean(content = newContent).asInstanceOf[T]
  }

  def unreify[T <: AnonymousCopybean](copybean: T, types: Set[CopybeanType]): T = {

    def removeUrl(fieldData: Any, copybean: T, fieldId: String): Map[String, String] = {
      val fieldMap = caster.anyToMapThen(fieldData, s"bean ${copybean.toString}", s"Field $fieldId") {
        caster.mapToMapStringString
      }
      fieldMap - "url"
    }

    val fields: Set[CopybeanFieldDef] = findImageAndFileFields(types)

    val undecoratedBean = modifyContent[T](copybean, fields) { (fieldData, field) =>
      removeUrl(fieldData, copybean, field.id)
    }

    val listFields = findListFieldsWithFileOrImage(types)

    modifyContent(undecoratedBean, listFields) { (fieldData, field) =>
      caster.anyToSeqThen(fieldData, s"bean ${copybean.toString}", s"Field ${field.id}") { (seq, _, _) =>
        seq.zipWithIndex.map(valueAndIndex => {
          val (value, index) = valueAndIndex
          removeUrl(value, copybean, s"${field.id}($index)")
        })
      }
    }

  }

}
