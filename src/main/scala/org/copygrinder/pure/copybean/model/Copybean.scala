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

  def copyAnonymousCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any]): AnonymousCopybean
}

trait Copybean extends AnonymousCopybean {
  val id: String

  def copyCopybean(enforcedTypeIds: Set[String], content: ListMap[String, Any], id: String): Copybean
}

trait ReifiedCopybean extends Copybean {
  val names: Map[String, String]
}

case class AnonymousCopybeanImpl(enforcedTypeIds: Set[String], content: ListMap[String, Any]) extends AnonymousCopybean {
  override def copyAnonymousCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any] = content): AnonymousCopybean = {
    copy(enforcedTypeIds = enforcedTypeIds, content = content)
  }
}

case class CopybeanImpl(id: String, enforcedTypeIds: Set[String], content: ListMap[String, Any]) extends Copybean {
  override def copyAnonymousCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any] = content): AnonymousCopybean = {
    copy(enforcedTypeIds = enforcedTypeIds, content = content)
  }

  override def copyCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any] = content, id: String = id): Copybean = {
    copy(enforcedTypeIds = enforcedTypeIds, content = content, id = id)
  }
}

case class ReifiedCopybeanImpl(enforcedTypeIds: Set[String], content: ListMap[String, Any], id: String,
 names: Map[String, String], types: Set[CopybeanType]) extends ReifiedCopybean {

  lazy val fields: ListMap[String, ReifiedField] = {
    content.foldLeft(ListMap[String, ReifiedField]())((result, idAndValue) => {
      val (id, value) = idAndValue
      val fieldDefs = types.flatMap(_.fields.flatMap(_.find(_.id == id)))
      val fieldDef = if (fieldDefs.isEmpty) {
        CopybeanFieldDef.cast(id, FieldType.Unknown)
      } else {
        fieldDefs.head
      }
      result + (id -> ReifiedField.cast(fieldDef, value))
    })
  }

  override def copyAnonymousCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any] = content): AnonymousCopybean = {
    copy(enforcedTypeIds = enforcedTypeIds, content = content)
  }

  override def copyCopybean(enforcedTypeIds: Set[String] = enforcedTypeIds, content: ListMap[String, Any] = content, id: String = id): Copybean = {
    copy(enforcedTypeIds = enforcedTypeIds, content = content, id = id)
  }
}

case class ReifiedField private(fieldDef: CopybeanFieldDef, value: Any)

object ReifiedField {
  def cast(fieldDef: CopybeanFieldDef, value: Any) = {
    new ReifiedField(fieldDef, value)
  }
}

