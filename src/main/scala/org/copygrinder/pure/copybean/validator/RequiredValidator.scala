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

class RequiredValidator extends Validator {

  override def validate(copybean: Copybean, args: ListMap[String, Any]): Unit = {

    val fields = args.getOrElse("fields", throw new TypeValidationException(s"Required validation args incorrectly defined.  Needed 'fields'.  args: $args"))

    fields match {
      case fields: Seq[_] => {
        fields.foreach {
          case field: String => checkField(copybean, field)
          case e => throw new TypeValidationException(s"Required validation args incorrectly defined.  Needed 'fields' value to be an array of strings but found: $e")
        }
      }
      case field: String => checkField(copybean, field)
      case e => throw new TypeValidationException(s"Required validation args incorrectly defined.  Needed 'fields' value to be an array but was: $e")
    }


  }

  protected def checkField(copybean: Copybean, field: String) = {
    copybean.content.find(f => f._1 == field) match {
      case Some(value) => value._2 match {
        case str: String =>
          if (str.isEmpty) {
            throw new TypeValidationException(s"Field '$field' is required but was empty")
          }
        case _ =>
      }
      case None => throw new TypeValidationException(s"Field '$field' was not defined but is required")
    }
  }

}