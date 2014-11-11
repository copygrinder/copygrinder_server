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
import play.api.libs.functional.syntax._
import play.api.libs.json._


trait JsonReads extends JsonReadUtils {

  implicit val fieldTypeReads = enumReads(FieldType)

  implicit val copybeanFieldDefReads = Json.reads[CopybeanFieldDef]

  implicit val anonymousCopybeanValidatorDefReads = Json.reads[CopybeanValidatorDef]

  implicit val cardinalityReads = enumReads(Cardinality)

  implicit val copybeanTypeRead = readWrapper(Json.reads[CopybeanType])

  implicit val anonymousCopybeanReads: Reads[AnonymousCopybean] = readWrapper((
   (JsPath \ "enforcedTypeIds").read[Set[String]] and
    (JsPath \ "contains").read[JsObject]
   )(AnonymousCopybeanImpl.apply _)
  )


  implicit val copybeanReads = readWrapper(Json.reads[CopybeanImpl])

}
