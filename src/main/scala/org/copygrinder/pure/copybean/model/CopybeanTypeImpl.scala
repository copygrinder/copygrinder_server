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

import org.copygrinder.pure.copybean.model.Cardinality.Cardinality

trait CopybeanTypeWithAnonValDefs {
  val id: String
  val singularTypeNoun: String
  val pluralTypeNoun: Option[String]
  val instanceNameFormat: Option[String]
  val fields: Seq[CopybeanFieldDef]
  val validators: Seq[AnonymousCopybeanValidatorDef]
  val cardinality: Cardinality
}

trait CopybeanType extends CopybeanTypeWithAnonValDefs {
  val validators: Seq[CopybeanValidatorDef]

}

case class CopybeanTypeWithAnonValDefsImpl(
  id: String,
  singularTypeNoun: String,
  pluralTypeNoun: Option[String] = None,
  instanceNameFormat: Option[String] = None,
  fields: Seq[CopybeanFieldDef] = Seq(),
  validators: Seq[AnonymousCopybeanValidatorDefImpl] = Seq(),
  cardinality: Cardinality = Cardinality.Many
  ) extends CopybeanTypeWithAnonValDefs

case class CopybeanTypeImpl(anonType: CopybeanTypeWithAnonValDefsImpl, validators: Seq[CopybeanValidatorDefImpl]) extends CopybeanType {
  override val id = anonType.id
  override val singularTypeNoun = anonType.singularTypeNoun
  override val cardinality = anonType.cardinality
  override val instanceNameFormat = anonType.instanceNameFormat
  override val fields = anonType.fields
  override val pluralTypeNoun = anonType.pluralTypeNoun
}


object Cardinality extends Enumeration {

  type Cardinality = Value

  val One, Many = Value

}
