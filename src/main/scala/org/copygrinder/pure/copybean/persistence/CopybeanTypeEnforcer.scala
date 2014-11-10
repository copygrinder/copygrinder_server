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
package org.copygrinder.pure.copybean.persistence

import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.validator.RequiredValidator
import play.api.libs.json.{JsNumber, JsString}

class CopybeanTypeEnforcer() {

  val requiredValidator = new RequiredValidator

  def enforceType(copybeanType: CopybeanType, copybean: Copybean): Unit = {
    copybeanType.fields.map(_.map { fieldDef =>
      checkField(fieldDef, copybean)
    })
    checkValidators(copybeanType, copybean)
  }

  protected def checkField(fieldDef: CopybeanFieldDef, copybean: Copybean) = {

    val fieldId = fieldDef.id

    val valueOpt = copybean.contains.fields.find(field => field._1 == fieldId)

    if (valueOpt.isDefined) {
      val value = valueOpt.get._2
      val fType = fieldDef.`type`

      value match {
        case string: JsString =>
          if (fType == FieldType.Integer) {
            throw new TypeValidationException(s"$fieldId must be an Integer but was the String: $value")
          }
        case int: JsNumber =>
          if (fType == FieldType.String) {
            throw new TypeValidationException(s"$fieldId must be a String but was the Integer: $value")
          }
        case _ =>
          throw new TypeValidationException(s"$fieldId with value $value was an unexpected type: " + value.getClass)
      }
    }

  }

  protected def checkValidators(copybeanType: CopybeanType, copybean: Copybean): Unit = {
    copybeanType.validators.map(_.map({ validatorDef =>
      val vType = validatorDef.`type`
      vType match {
        case "required" =>
          requiredValidator.validate(copybean, validatorDef.args)
        case _ =>
          throw new TypeValidationException("Unknown validator type '$vType'")
      }
    }))
  }

}