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
package org.copygrinder.impure.api

import java.lang.reflect.InvocationTargetException

import org.json4s.JsonAST.{JArray, JObject, JValue}
import org.json4s.jackson.JsonMethods._
import org.json4s.{Formats, MappingException}
import spray.http.{HttpCharsets, HttpEntity, MediaTypes}
import spray.httpx.unmarshalling.Unmarshaller

class ValidatingUnmarshaller(implicit formats: Formats) {

  def json4sUnmarshaller[T: Manifest]: Unmarshaller[T] = Unmarshaller[T](MediaTypes.`application/json`) {
    case x: HttpEntity.NonEmpty =>

      val str = x.asString(defaultCharset = HttpCharsets.`UTF-8`)
      val json = parse(str)

      val result = try json.extract[T]
      catch {
        case MappingException("unknown error", ite: InvocationTargetException) => throw ite.getCause
      }


      validateUnknownProperties(json)

      result
  }

  protected def validateUnknownProperties[T: Manifest](json: JValue) {
    import scala.reflect.runtime.universe._
    if (json.isInstanceOf[JObject]) {

      val thisType = typeOf[T]
      val typeArgs = thisType.typeArgs
      val members = if (typeArgs.isEmpty) {
        thisType.members
      } else {
        typeArgs(0).members
      }

      val methods = members.collect {
        case m: MethodSymbol if (m.isCaseAccessor) => m.name.toString
      }.toList

      val jsonKeys = json.asInstanceOf[JObject].obj.map(_._1)

      val missingJsonKeys = jsonKeys.filterNot(key => methods.contains(key))

      if (missingJsonKeys.nonEmpty) {
        throw new MappingException("One or more properties is invalid: " + missingJsonKeys.mkString(","))
      }

    } else if (json.isInstanceOf[JArray]) {
      json.asInstanceOf[JArray].arr.foreach(validateUnknownProperties(_))
    }
  }
}