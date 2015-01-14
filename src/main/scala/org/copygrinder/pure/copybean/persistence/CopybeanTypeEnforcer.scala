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
import org.copygrinder.pure.copybean.validator.FieldValidator

class CopybeanTypeEnforcer() {

  def enforceType(
   copybeanType: CopybeanType,
   copybean: Copybean,
   validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]
   ): Unit = {
    copybeanType.fields.map(_.map { fieldDef =>
      checkField(fieldDef, copybean)
    })
    checkValidators(copybeanType, copybean, validatorBeans, validatorClassInstances)
  }

  protected def checkField(fieldDef: CopybeanFieldDef, copybean: Copybean) = {

    val fieldId = fieldDef.id

    val valueOpt = copybean.content.find(field => field._1 == fieldId)

    if (valueOpt.isDefined) {
      val value = valueOpt.get._2
      val fType = fieldDef.`type`

      value match {
        case string: String =>
          if (fType == FieldType.Integer) {
            throw new TypeValidationException(s"$fieldId must be an Integer but was the String: $value")
          }
        case int: Int =>
          if (fType == FieldType.String) {
            throw new TypeValidationException(s"$fieldId must be a String but was the Integer: $value")
          }
        case _ =>
          throw new TypeValidationException(s"$fieldId with value $value was an unexpected type: ${value.getClass}")
      }
    }

  }

  protected def checkValidators(
   copybeanType: CopybeanType,
   copybean: Copybean, validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]) = {
    copybeanType.fields.map(_.map(field => field.validators.map(_.map({ validatorDef =>
      val typeId = validatorDef.`type`
      val validator = validatorBeans.getOrElse(s"validator.$typeId",
        throw new TypeValidationException(s"Couldn't find a validator of type '$typeId'")
      )
      if (validator.enforcedTypeIds.contains("classBackedFieldValidator")) {
        checkClassBackedValidator(copybean, field.id, validator, validatorDef, validatorClassInstances)
      } else {
        throw new TypeValidationException(s"Couldn't execute validator '${validator.id}'")
      }
    }))))
  }

  protected def checkClassBackedValidator(
   copybean: Copybean,
   field: String,
   validator: Copybean,
   validatorDef: CopybeanFieldValidatorDef,
   validatorClassInstances: Map[String, FieldValidator]) = {
    val className = validator.content.getOrElse("class",
      throw new TypeValidationException(s"Couldn't find a class for validator '${validator.id}'")
    )

    className match {
      case classNameString: String => {
        val validator = validatorClassInstances.getOrElse(classNameString,
          throw new TypeValidationException(s"Couldn't find a class for validator '${classNameString}'")
        )
        validator.validate(copybean, field, validatorDef.args)
      }
      case x => throw new TypeValidationException(
        s"Validator '${validator.id}' did not specify class as a String but the value '$x' which is a ${x.getClass}"
      )
    }
  }


}