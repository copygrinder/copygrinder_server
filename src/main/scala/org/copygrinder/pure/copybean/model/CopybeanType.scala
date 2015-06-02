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
package org.copygrinder.pure.copybean.model

import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.Cardinality.Cardinality
import org.copygrinder.pure.copybean.persistence.{PredefinedCopybeanTypes, UntypedCaster}

import scala.collection.immutable.ListMap

trait CopybeanType extends ReifiedCopybean {

  lazy val typeId = getFieldAsString("typeId").get

  lazy val displayName = getFieldAsString("displayName")

  lazy val instanceNameFormat = getFieldAsString("instanceNameFormat")

  lazy val fields: Option[Seq[CopybeanFieldDef]] = calcTypeFields()

  lazy val cardinality = Cardinality.withName(getFieldAsString("cardinality").get)

  protected val caster = new UntypedCaster

  protected def calcTypeFields(): Option[Seq[CopybeanFieldDef]] = getFieldAsList("fields").flatMap { fields =>
    val fieldDefs = fields.map {
      case m: Map[_, _] =>

        val map = m.asInstanceOf[Map[Any,Any]]

        def getString(key: Any) = {
          caster.mapGetToString(map, key, s"Type $id, $typeId", "fields")
        }

        val fieldId = getString("id")

        val fieldType = FieldType.withName(getString("type"))

        val displayName = getStringOpt("displayName", map)

        val attributes = try {
          Option(caster.mapGetToMap(map, "attributes", s"Type $id", "fields").asInstanceOf[ListMap[String, Any]])
        } catch {
          case e: TypeValidationException => None
        }

        val validators: Option[Seq[CopybeanFieldValidatorDef]] = getValidators(map)

        CopybeanFieldDef.cast(fieldId, fieldType, displayName, attributes, validators)

      case o => throw new CopygrinderRuntimeException(o.toString + " is not a map field.")
    }

    if (fieldDefs.isEmpty) {
      None
    } else {
      Option(fieldDefs)
    }

  }

  protected def getValidators(map: Map[Any, Any]): Option[Seq[CopybeanFieldValidatorDef]] = {
    val validators = try {
      val validatorMaps = caster.mapGetToSeqThen(map, "validators", s"Type $id", "fields")(caster.seqToMap)
      val defs = validatorMaps.map { validatorMap =>
        val validatorType = caster.mapGetToString(validatorMap, "type", s"Type $id", "fields.validators")
        val args = try {
          Option(caster.mapGetToMap(validatorMap, "args", s"Type $id", "fields.validators")
           .asInstanceOf[ListMap[String, Any]])
        } catch {
          case e: TypeValidationException => None
        }
        CopybeanFieldValidatorDef(validatorType, args)
      }

      if (defs.isEmpty) {
        None
      } else {
        Option(defs)
      }
    } catch {
      case e: TypeValidationException => None
    }
    validators
  }

  protected def getStringOpt(key: Any, map: Map[Any,Any]) = {
    try {
      Option(caster.mapGetToString(map, key, s"Type $id", "fields"))
    } catch {
      case e: TypeValidationException => None
    }
  }

  protected def getFieldAsString(id: String) = {
    content.get(id).map {
      case s: String => s
      case o => throw new CopygrinderRuntimeException(o.toString + " is not a string field.")
    }
  }

  protected def getFieldAsList(id: String) = {
    content.get(id).map {
      case seq: Seq[_] => seq
      case o => throw new CopygrinderRuntimeException(o.toString + " is not a list field.")
    }
  }

}

object CopybeanType {

  protected val typeType = new PredefinedCopybeanTypes().typeType

  def apply(typeId: String,
   displayName: Option[String] = None,
   instanceNameFormat: Option[String] = None,
   fields: Option[Seq[CopybeanFieldDef]] = None,
   cardinality: Cardinality
   ): CopybeanType = {

    val content1: ListMap[String, Any] = ListMap(
      "typeId" -> typeId,
      "cardinality" -> cardinality.toString
    )

    val content2 = if (displayName.isDefined) {
      content1 + ("displayName" -> displayName.get)
    } else {
      content1
    }

    val content3 = if (instanceNameFormat.isDefined) {
      content2 + ("instanceNameFormat" -> instanceNameFormat.get)
    } else {
      content2
    }

    val content4 = if (fields.isDefined) {

      val fieldSeq: Seq[ListMap[String, Any]] = getFields(fields)

      content3 + ("fields" -> fieldSeq)
    } else {
      content3
    }

    new CopybeanTypeImpl(Set("type"), content4, typeId, Set(typeType))
  }

  protected def getFields(fields: Option[Seq[CopybeanFieldDef]]): Seq[ListMap[String, Any]] = {
    val fieldSeq = fields.map(_.map { field =>
      val fieldMap1: ListMap[String, Any] = ListMap("id" -> field.id, "type" -> field.`type`.toString)

      val fieldMap2 = if (field.displayName.isDefined) {
        fieldMap1 + ("displayName" -> field.displayName.get)
      } else {
        fieldMap1
      }

      val fieldMap3 = if (field.attributes.isDefined) {
        fieldMap2 + ("attributes" -> field.attributes.get)
      } else {
        fieldMap2
      }

      val fieldMap4 = if (field.validators.isDefined) {

        val validators = field.validators.map(_.map { validator =>

          val validatorMap1: ListMap[String, Any] = ListMap("type" -> validator.`type`.toString)
          val validatorMap2 = if (validator.args.isDefined) {
            validatorMap1 + ("args" -> validator.args.get)
          } else {
            validatorMap1
          }
          validatorMap2
        }).toSeq.flatten

        fieldMap3 + ("validators" -> validators)
      } else {
        fieldMap3
      }

      fieldMap4
    }).toSeq.flatten
    fieldSeq
  }

  def apply(bean: ReifiedCopybean): CopybeanType = {
    if (bean.id == "type" || bean.enforcedTypeIds.contains("type")) {
      new CopybeanTypeImpl(bean.enforcedTypeIds, bean.content, bean.id, bean.types)
    } else {
      throw new JsonInputException(
        s"Bean '${bean.id}' is being treated as a type but doesn't have 'type' in it's enforcedTypeIds.")
    }
  }

}

class CopybeanTypeImpl(override val enforcedTypeIds: Set[String], override val content: ListMap[String, Any],
 override val id: String, override val types: Set[CopybeanType])
 extends ReifiedCopybeanImpl(enforcedTypeIds, content, id, types) with CopybeanType

object Cardinality extends Enumeration {

  type Cardinality = Value

  val One, Many = Value

}
