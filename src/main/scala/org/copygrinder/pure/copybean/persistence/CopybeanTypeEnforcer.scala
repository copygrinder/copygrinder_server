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
import org.copygrinder.pure.copybean.model.{Copybean, CopybeanFieldDef, CopybeanType, FieldType}

class CopybeanTypeEnforcer() {

  def enforceType(copybeanType: CopybeanType, copybean: Copybean): Unit = {
    copybeanType.fieldDefs.map { fieldDef =>
      checkField(fieldDef, copybean)
    }
  }

  protected def checkField(fieldDef: CopybeanFieldDef, copybean: Copybean) = {

    val fieldId = fieldDef.id

    val valueOpt = copybean.contains.values.get(fieldId)

    if (valueOpt.isDefined) {
      val value = valueOpt.get
      val fType = fieldDef.`type`

      value match {
        case string: String =>
          if (fType == FieldType.Integer) {
            throw new TypeValidationException(s"$fieldId must be an Integer but was the String: $value")
          }
        case int: BigInt =>
          if (fType == FieldType.String) {
            throw new TypeValidationException(s"$fieldId must be a String but was the Integer: $value")
          }
        case _ =>
          throw new TypeValidationException(s"$fieldId with value $value was an unexpected type: " + value.getClass)
      }
    }

  }

}