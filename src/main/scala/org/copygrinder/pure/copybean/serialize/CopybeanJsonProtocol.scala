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
import org.copygrinder.pure.copybean.Copybean
import scala.util.parsing.json.JSONType
import scala.util.parsing.json.JSONObject
import scala.util.parsing.json.JSON

object CopybeanJsonProtocol extends DefaultJsonProtocol {

  implicit object CopybeanFieldDefJsonFormat extends JsonFormat[Any] {

    def read(value: JsValue) = value match {
      case JsString(pType) =>
        ""
      case _ => deserializationError("PrimitiveType expected")
    }

    def write(value: Any) = {
      value match {
        case map: Map[_, _] =>
          value.asInstanceOf[Map[String, Any]].toJson
        case _: String =>
          JsString(value.toString)
        case _: Int | Long | Double | Float =>
          JsNumber(value.toString)
        case b: Boolean =>
          JsBoolean(b)
        case _ =>
          serializationError("Can't serialize " + value)
      }
    }
  }

  implicit val copybeanFormat = jsonFormat3(Copybean)
}