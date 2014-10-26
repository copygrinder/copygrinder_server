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
package org.copygrinder.pure.copybean.model

import org.json4s.JsonAST.JValue

trait AnonymousCopybeanValidatorDef {
  val `type`: String
  val args: Map[String, JValue]
}

trait CopybeanValidatorDef extends AnonymousCopybeanValidatorDef {
  val id: String
}

case class AnonymousCopybeanValidatorDefImpl(`type`: String, args: Map[String, JValue]) extends AnonymousCopybeanValidatorDef {

}

case class CopybeanValidatorDefImpl(id: String, anonDef: AnonymousCopybeanValidatorDefImpl) extends CopybeanValidatorDef {

  def this(id: String, `type`: String, args: Map[String, JValue]) = {
    this(id, new AnonymousCopybeanValidatorDefImpl(`type`, args))
  }

  override val `type` = anonDef.`type`

  override val args = anonDef.args

}