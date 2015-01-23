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

class TypeEnforcer() {

  protected val caster = new UntypedCaster()

  def enforceType(
   copybeanType: CopybeanType
   ): Unit = {
    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.map { fieldDef =>
        val fType = fieldDef.`type`
        if (fType == FieldType.Reference) {
          checkRefsAttrs(fieldDef)
        }
      }
    }
  }

  protected def checkRefsAttrs(fieldDef: CopybeanFieldDef): Option[String] = {
    val castAttrs = caster.castAttr[Seq[Map[String, Either[String, Seq[String]]]]](fieldDef, "refs")
    castAttrs.foreach(ref => {

      val validationTypes = ref.get("refValidationTypes").getOrElse(
        throw new TypeValidationException(s"${fieldDef.id} requires attribute refValidationTypes")
      ).right.get

      val displayType = ref.get("refDisplayType").getOrElse(
        throw new TypeValidationException(s"${fieldDef.id} requires attribute refDisplayType")
      ).left.get

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

}