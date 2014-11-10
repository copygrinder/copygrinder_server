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
package org.copygrinder.pure.copybean.validator

import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.model.Copybean
import play.api.libs.json.{JsNull, JsString, JsValue}

class RequiredValidator extends Validator {

  protected val falseyValues = Seq(0, false, "false", "0", "f", "no", "n")

  override def validate(copybean: Copybean, args: Map[String, JsValue]): Unit = {

    args.foreach { arg =>
      val (field, argValue) = arg
      if (!falseyValues.contains(argValue.toString())) {
        copybean.contains.fields.find(f => f._1 == field) match {
          case Some(value) => value._2 match {
            case JsNull =>
              throw new TypeValidationException(s"Field '$field' is required but was null")
            case jString: JsString =>
              if (jString.value.isEmpty) {
                throw new TypeValidationException(s"Field '$field' is required but was empty")
              }
            case _ =>
          }
          case None => throw new TypeValidationException(s"Field '$field' was not defined but is required")
        }
      }
    }

  }

}