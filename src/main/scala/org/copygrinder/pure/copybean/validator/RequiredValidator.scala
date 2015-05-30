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

import scala.collection.immutable.ListMap

class RequiredValidator extends FieldValidator {

  override def validate(copybean: Copybean, field: String, args: Option[ListMap[String, Any]]): Unit = {

    val fieldValue = copybean.content.getOrElse(field,
      throw new TypeValidationException(s"Field '$field' was not defined but is required in bean $copybean"))

    fieldValue match {
      case str: String =>
        if (str.isEmpty) {
          throw new TypeValidationException(s"Field '$field' is required but was empty")
        }
      case seq: Seq[_] =>
        if (seq.isEmpty) {
          throw new TypeValidationException(s"Field '$field' is required but was empty")
        }
      case int: Int =>
      case long: Long =>
      case other => throw new TypeValidationException(s"Field '$field' is required but we can't validate $other")
    }
  }

}