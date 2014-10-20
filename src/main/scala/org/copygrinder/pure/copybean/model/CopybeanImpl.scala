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

import org.json4s.JsonAST.JObject


trait AnonymousCopybean {
  val enforcedTypeIds: Set[String]
  val contains: JObject
}

trait Copybean extends AnonymousCopybean {
  val id: String
}

trait ReifiedCopybean extends Copybean {
  val names: Map[String, String]
}

case class AnonymousCopybeanImpl(enforcedTypeIds: Set[String], contains: JObject) extends AnonymousCopybean {

}

case class CopybeanImpl(anonCopybean: AnonymousCopybeanImpl, id: String) extends Copybean {

  override val enforcedTypeIds = anonCopybean.enforcedTypeIds

  override val contains = anonCopybean.contains

  lazy val containsMap = contains.obj.toMap

}

case class ReifiedCopybeanImpl(copybean: CopybeanImpl, names: Map[String, String]) extends ReifiedCopybean {

  override val enforcedTypeIds = copybean.enforcedTypeIds

  override val contains = copybean.contains

  override val id = copybean.id

}
