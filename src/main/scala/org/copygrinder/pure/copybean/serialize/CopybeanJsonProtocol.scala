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
package org.copygrinder.pure.copybean.serialize

import spray.json._
import spray.json.DefaultJsonProtocol._
import org.copygrinder.pure.copybean.CopybeanType
import org.copygrinder.pure.copybean.CopybeanFieldDef
import org.copygrinder.pure.copybean.PrimitiveType._
import org.copygrinder.pure.copybean.PrimitiveType
import org.copygrinder.pure.copybean.CopybeanType
import org.copygrinder.pure.copybean.CopybeanFieldDef
import org.copygrinder.pure.copybean.PrimitiveCopybeanFieldDef
import org.copygrinder.pure.copybean.validator.CopybeanValidator
import org.copygrinder.pure.copybean.Copybean

object CopybeanJsonProtocol extends DefaultJsonProtocol {

  implicit object PrimitiveTypeJsonFormat extends JsonFormat[PrimitiveType] {

    def read(value: JsValue) = value match {
      case JsString(pType) =>
        PrimitiveType.withName(pType)
      case _ => deserializationError("PrimitiveType expected")
    }

    def write(pType: PrimitiveType) = {
      JsString(pType.toString())
    }
  }

  implicit object CopybeanFieldDefJsonFormat extends JsonFormat[CopybeanFieldDef] {

    def read(value: JsValue) = value match {
      case JsString(pType) =>
        new PrimitiveCopybeanFieldDef("", PrimitiveType.Blob)
      case _ => deserializationError("PrimitiveType expected")
    }

    def write(pType: CopybeanFieldDef) = {
      JsString(pType.toString())
    }
  }
  
  implicit object CopybeanValidatorJsonFormat extends JsonFormat[CopybeanValidator] {

    def read(value: JsValue) = value match {
      case JsString(validator) =>
        new Object().asInstanceOf[CopybeanValidator]
      case _ => deserializationError("PrimitiveType expected")
    }

    def write(pType: CopybeanValidator) = {
      JsString(pType.toString())
    }
  }

  implicit val copybeanTypeFormat = jsonFormat3(CopybeanType)
  implicit val copybeanFormat = jsonFormat3(Copybean)
}