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
import org.copygrinder.pure.copybean.model.ReifiedField.{ReifiedFieldSupport, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.validator.FieldValidator

class CopybeanTypeEnforcer() {


  def enforceTypes(copybean: ReifiedCopybean, validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]): Map[String, CopybeanFieldDef] = {

    val copybeanTypes = copybean.types

    val reifiedFields = copybean.reifiedFields.values
    checkFields(reifiedFields)

    copybeanTypes.foreach(copybeanType => {
      if (copybeanType.fields.isDefined) {
        copybeanType.fields.get.foreach { fieldDef =>
          checkValidators(fieldDef, copybean, validatorBeans, validatorClassInstances)
        }
      }
    })

    getRefs(reifiedFields)
  }

  protected def checkFields(fields: Iterable[ReifiedField]) = {
    fields.foreach(field => {
      field.asInstanceOf[ReifiedFieldSupport].castVal.toString
    })
  }

  protected def getRefs(fields: Iterable[ReifiedField]) = {
    fields.flatMap {
      case r: ReferenceReifiedField =>
        if (r.castVal.nonEmpty) {
          Option((r.castVal, r.fieldDef))
        } else {
          None
        }
      case _ => None
    }.toMap
  }


  protected def checkValidators(
   field: CopybeanFieldDef,
   copybean: Copybean, validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]) = {
    field.validators.foreach(_.foreach({
      validatorDef =>
        val typeId = validatorDef.`type`
        val validator = validatorBeans.getOrElse(s"validator.$typeId",
          throw new TypeValidationException(s"Couldn't find a validator of type '$typeId'")
        )
        if (validator.enforcedTypeIds.contains("classBackedFieldValidator")) {
          checkClassBackedValidator(copybean, field.id, validator, validatorDef, validatorClassInstances)
        } else {
          throw new TypeValidationException(s"Couldn't execute validator '${
            validator.id
          }'")
        }
    }))
  }

  protected def checkClassBackedValidator(
   copybean: Copybean,
   field: String,
   validator: Copybean,
   validatorDef: CopybeanFieldValidatorDef,
   validatorClassInstances: Map[String, FieldValidator]) = {
    val className = validator.content.getOrElse("class",
      throw new TypeValidationException(
        s"Couldn't find a class for validator '${validator.id}'"
      )
    )

    className match {
      case classNameString: String =>
        val validator = validatorClassInstances.getOrElse(classNameString,
          throw new TypeValidationException(s"Couldn't find a class for validator '${
            classNameString
          }'")
        )
        validator.validate(copybean, field, validatorDef.args)
      case x => throw new TypeValidationException(
        s"Validator '${
          validator.id
        }' did not specify class as a String but the value '$x' which is a ${
          x.getClass
        }"
      )
    }
  }

}