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

import scala.reflect.runtime.universe._

class CopybeanTypeEnforcer() {

  def enforceType(
   copybeanType: CopybeanType,
   copybean: Copybean,
   validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]
   ): Set[String] = {
    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.map { fieldDef =>

        val fieldId = fieldDef.id
        val valueOpt = copybean.content.find(field => field._1 == fieldId)
        val ref = if (valueOpt.isDefined) {
          val value = valueOpt.get._2
          val fType = fieldDef.`type`
          checkField(fieldId, value, fType)
          if (fType == FieldType.Reference) {
            checkRefsAttrs(fieldDef, value)
            checkRefs(fieldDef, value)
          } else {
            None
          }
        } else {
          None
        }

        checkValidators(fieldDef, copybean, validatorBeans, validatorClassInstances)
        ref
      }.flatten.toSet
    } else {
      Set()
    }
  }

  protected def checkField(fieldId: String, value: Any, fType: FieldType.FieldType) = {
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

  /*
   * TODO: This should run when types are created, probably not when beans are created.
   */
  protected def checkRefsAttrs(fieldDef: CopybeanFieldDef, value: Any): Option[String] = {
    val castAttrs = castAttr[Seq[Map[String, Either[String, Seq[String]]]]](fieldDef, "refs")
    castAttrs.foreach(ref => {

      val validationTypes = ref.get("refValidationTypes").get.right.get
      val displayType = ref.get("refDisplayType").get.left.get

      if (validationTypes.isEmpty) {
        throw new TypeValidationException(s"${fieldDef.id} requires an array for attribute refValidationTypes")
      }
      if (displayType.isEmpty) {
        throw new TypeValidationException(s"${fieldDef.id} requires attribute refDisplayType")
      }

      if (!validationTypes.contains(displayType)) {
        throw new TypeValidationException(
          s"${fieldDef.id} has a refDisplayType '$displayType' that is not in refValidationTypes '${validationTypes.mkString}'"
        )
      }

    })
    None
  }

  protected def castAttr[T](fieldDef: CopybeanFieldDef, attr: String)(implicit typeTag: TypeTag[T]): T = {

    val attrs = fieldDef.attributes.getOrElse(
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $attr")
    )

    val data = attrs.getOrElse(attr,
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $attr")
    )

    val castData = doCastAttr(data, fieldDef, attr, typeTag.tpe)
    castData.asInstanceOf[T]
  }

  protected def doCastAttr(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type): Any = {

    tpe.typeConstructor.toString match {
      case s if s.endsWith("Seq") => {
        handleSeqCast(data, fieldDef, parent, tpe)
      }
      case m if m.endsWith("Map") => {
        handleMapCast(data, fieldDef, parent, tpe)
      }
      case e if e.endsWith("Either") => {
        handleEitherCast(data, fieldDef, parent, tpe)
      }
      case "String" => data
      case other => throw new TypeValidationException(s"${fieldDef.id} has unknown argument type '$other'")
    }

  }

  protected def handleSeqCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    if (data.isInstanceOf[Seq[_]]) {
      val seq = data.asInstanceOf[Seq[Any]].zipWithIndex
      seq.map(pair => {
        val (innerData, index) = pair
        val newParent = s"$parent[$index]"
        doCastAttr(innerData, fieldDef, newParent, tpe.typeArgs.head)
      })
    } else {
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $parent to be an array")
    }
  }

  protected def handleMapCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    if (data.isInstanceOf[Map[_, _]]) {
      val map = data.asInstanceOf[Map[Any, Any]]
      map.map(entry => {
        if (entry._1.isInstanceOf[String]) {
          entry._1 -> doCastAttr(entry._2, fieldDef, parent + "." + entry._1, tpe.typeArgs(1))
        } else {
          throw new TypeValidationException(s"${fieldDef.id} requires attribute $parent.${entry._1} to be an string")
        }
      })
    } else {
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $parent to be an object")
    }
  }

  protected def handleEitherCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    val isLeft = checkEither(data, fieldDef, parent, tpe.typeArgs(0))
    val isRight = checkEither(data, fieldDef, parent, tpe.typeArgs(1))
    if (isLeft) {
      Left(doCastAttr(data, fieldDef, parent, tpe.typeArgs(0)))
    } else if (isRight) {
      Right(doCastAttr(data, fieldDef, parent, tpe.typeArgs(1)))
    } else {
      throw new TypeValidationException(
        s"${fieldDef.id} attribute $parent is neither ${tpe.typeArgs(0)} nor ${tpe.typeArgs(1)}"
      )
    }
  }

  protected def checkEither(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type): Boolean = {
    tpe.typeConstructor.toString match {
      case "String" => {
        if (data.isInstanceOf[String]) {
          true
        } else {
          false
        }
      }
      case s if s.endsWith("Seq") => {
        if (data.isInstanceOf[Seq[_]]) {
          true
        } else {
          false
        }
      }
      case other =>
        throw new TypeValidationException(
          s"${fieldDef.id} attribute $parent has an unknown Either type '$other'"
        )
    }
  }


  protected def checkRefs(fieldDef: CopybeanFieldDef, value: Any): Option[String] = {
    value match {
      case string: String =>
        if (fieldDef.`type` == FieldType.Reference && string.nonEmpty) {
          if (!string.startsWith("!REF!:")) {
            throw new TypeValidationException(
              s"${fieldDef.id} must be an Reference but didn't start with !REF!: $string"
            )
          } else {
            Option(string.replace("!REF!:", ""))
          }
        } else {
          None
        }
      case _ => None
    }
  }

  protected def checkValidators(
   field: CopybeanFieldDef,
   copybean: Copybean, validatorBeans: Map[String, Copybean],
   validatorClassInstances: Map[String, FieldValidator]) = {
    field.validators.map(_.map({
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
      case classNameString: String => {
        val validator = validatorClassInstances.getOrElse(classNameString,
          throw new TypeValidationException(s"Couldn't find a class for validator '${
            classNameString
          }'")
        )
        validator.validate(copybean, field, validatorDef.args)
      }
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