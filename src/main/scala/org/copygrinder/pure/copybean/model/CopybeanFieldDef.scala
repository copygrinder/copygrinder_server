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

import scala.collection.immutable.ListMap

case class CopybeanFieldDef private(
 id: String,
 `type`: FieldType.FieldType,
 displayName: Option[String] = None,
 attributes: Option[ListMap[String, Any]] = None,
 validators: Option[Seq[CopybeanFieldValidatorDef]] = None
 )

object CopybeanFieldDef {

  import FieldType._

  def cast(id: String,
   `type`: FieldType.FieldType,
   displayName: Option[String] = None,
   attributes: Option[ListMap[String, Any]] = None,
   validators: Option[Seq[CopybeanFieldValidatorDef]] = None
   ): CopybeanFieldDef = {
    `type` match {
      case Reference => new CopybeanFieldDef(id, `type`, displayName, attributes, validators) with ReferenceType
      case List => new CopybeanFieldDef(id, `type`, displayName, attributes, validators) with ListType
      case _ => new CopybeanFieldDef(id, `type`, displayName, attributes, validators)
    }
  }
}

object FieldType extends Enumeration {

  type FieldType = Value

  val String, Integer, Long, Reference, File, Image, List, Html, Unknown = Value

}

trait FieldTypeSupport {
  val id: String

  val attributes: Option[ListMap[String, Any]]

  protected val caster = new UntypedCaster()
}

trait ReferenceType extends FieldTypeSupport {

  case class Ref(validationTypes: Set[String], displayType: String)

  val refs = {
    caster.optToMapThen(attributes, s"Field $id", "attributes") {
      caster.mapGetToSeqThen(_, "refs", _, _) {
        caster.seqToMapThen(_, _, _) { (map, fieldId, targetId) =>
          val validationTypes = caster.mapGetToSeqThen(map, "refValidationTypes", fieldId, targetId)(
            caster.seqToSeqString)
          val displayType = caster.mapGetToString(map, "refDisplayType", fieldId, targetId)

          if (!validationTypes.contains(displayType)) {
            throw new TypeValidationException(
              s"$fieldId has refDisplayType '$displayType' not in refValidationTypes '${validationTypes.mkString}'"
            )
          }

          new Ref(validationTypes.toSet, displayType)
        }
      }
    }
  }
}

trait ListType extends FieldTypeSupport {

  val listType = {
    caster.optToMapThen(attributes, s"Field $id", "attributes") { (map, fieldId, targetId) =>
      val listType = caster.mapGetToString(map, "listType", fieldId, targetId)
      CopybeanFieldDef.cast(id, FieldType.withName(listType), None, attributes)
      listType
    }
  }


}
