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

import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.persistence.UntypedCaster

case class ReifiedField (fieldDef: CopybeanFieldDef, value: Any, parent: String)

object ReifiedField {

  protected val caster = new UntypedCaster()

  def cast(fieldDef: CopybeanFieldDef, value: Any, parent: String): ReifiedField with ReifiedFieldSupport = {

    fieldDef.`type` match {
      case FieldType.String | FieldType.Html => new ReifiedField(fieldDef, value, parent) with StringReifiedField
      case FieldType.Integer => new ReifiedField(fieldDef, value, parent) with IntReifiedField
      case FieldType.Long => new ReifiedField(fieldDef, value, parent) with LongReifiedField
      case FieldType.Boolean => new ReifiedField(fieldDef, value, parent) with BooleanReifiedField
      case FieldType.Reference => new ReifiedField(fieldDef, value, parent) with ReferenceReifiedField
      case FieldType.File | FieldType.Image => new ReifiedField(fieldDef, value, parent) with FileOrImageReifiedField
      case FieldType.List => new ReifiedField(fieldDef, value, parent) with ListReifiedField
      case FieldType.Unknown => new ReifiedField(fieldDef, value, parent) with UnknownReifiedField
    }

  }

  trait ReifiedFieldSupport {
    val value: Any

    val fieldDef: CopybeanFieldDef

    val parent: String

    val target = s"Field ${fieldDef.id}"

    val castVal: Any
  }

  trait StringReifiedField extends ReifiedFieldSupport {
    lazy val castVal = {
      if (value == null) { //scalastyle:ignore
        ""
      } else {
        caster.anyToString(value, parent, target)
      }
    }
  }

  trait IntReifiedField extends ReifiedFieldSupport {
    lazy val castVal = caster.anyToInt(value, parent, target)
  }

  trait LongReifiedField extends ReifiedFieldSupport {
    lazy val castVal = caster.anyToLong(value, parent, target)
  }

  trait BooleanReifiedField extends ReifiedFieldSupport {
    lazy val castVal = caster.anyToBoolean(value, parent, target)
  }

  trait ReferenceReifiedField extends ReifiedFieldSupport {

    val refBean: Option[ReifiedCopybean] = None

    lazy val castVal = {
      caster.anyToMapThen(value, parent, target)(caster.mapGetToString(_, "ref", _, _))
    }
  }

  trait FileOrImageReifiedField extends ReifiedFieldSupport {
    lazy val map = caster.anyToMap(value, parent, target)
    lazy val filename = caster.mapGetToString(map, "filename", parent, target)
    lazy val hash = caster.mapGetToString(map, "hash", parent, target)

    case class FileDef(filename: String, hash: String)

    lazy val castVal = FileDef(filename, hash)
  }

  trait ListReifiedField extends ReifiedFieldSupport {

    private lazy val seq = caster.anyToSeq(value, parent, target)

    lazy val listType = fieldDef.asInstanceOf[ListType].listType

    lazy val castVal: Seq[ReifiedField] = seq.zipWithIndex.map(valueAndIndex => {
      val (value, index) = valueAndIndex
      val nestedFieldDef = CopybeanFieldDef.cast(
        s"${fieldDef.id}($index)", FieldType.withName(listType), None, fieldDef.attributes
      )
      val nestedReifiedField = cast(nestedFieldDef, value, parent)
      nestedReifiedField.castVal.toString
      nestedReifiedField
    })
  }

  trait UnknownReifiedField extends ReifiedFieldSupport {
    lazy val castVal = value
  }

}
