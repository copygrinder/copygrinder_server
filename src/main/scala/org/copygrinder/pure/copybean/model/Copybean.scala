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

import scala.collection.immutable.ListMap


trait AnonymousCopybean {
  val enforcedTypeIds: Set[String]
  val content: ListMap[String, Any]
}

trait Copybean extends AnonymousCopybean {
  val id: String
}

trait ReifiedCopybean extends Copybean {
  val names: Map[String, String]
}

case class AnonymousCopybeanImpl(enforcedTypeIds: Set[String], content: ListMap[String, Any]) extends AnonymousCopybean {

}

case class CopybeanImpl(enforcedTypeIds: Set[String], content: ListMap[String, Any], id: String) extends Copybean {

}

case class ReifiedCopybeanImpl(enforcedTypeIds: Set[String], content: ListMap[String, Any], id: String, names: Map[String, String]) extends ReifiedCopybean {

}
