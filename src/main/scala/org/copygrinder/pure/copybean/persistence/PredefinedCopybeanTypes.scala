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

import org.copygrinder.pure.copybean.model._
import play.api.libs.json.JsBoolean

class PredefinedCopybeanTypes {

  implicit def value2option[T](v: T): Option[T] = Option(v)

  lazy val predefinedTypes = Map(copygrinderAdminType.id -> copygrinderAdminType)

  val copygrinderAdminType = new CopybeanType(
    id = "copygrinderAdminMetatype",
    displayName = "Copygrinder Admin Metabean",
    fields = Seq(
      new CopybeanFieldDef("siloName", FieldType.String)
    ),
    cardinality = Cardinality.One,
    validators = Seq(
      new CopybeanValidatorDef(
        "requiredId", "required", Map("siloName" -> JsBoolean(true))
      )
    )
  )
}