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
package org.copygrinder.pure.copybean.search

import org.apache.lucene.document._
import org.copygrinder.pure.copybean.model.Copybean
import org.json4s.JInt
import org.json4s.JsonAST.JString

class DocumentBuilder {

  def buildDocument(copybean: Copybean): Document = {
    val doc = new Document

    val idField = new StringField("id", copybean.id, Field.Store.NO)
    doc.add(idField)

    copybean.enforcedTypeIds.foreach(typeId => {
      val typeField = new StringField("enforcedType", typeId, Field.Store.NO)
      doc.add(typeField)
    })

    copybean.values.obj.foreach(value => {
      val field = value._2 match {
        case string: JString =>
          new TextField("values." + value._1, string.s, Field.Store.NO)
        case int: JInt =>
          new IntField("values." + value._1, int.num.toInt, Field.Store.NO)
      }
      doc.add(field)
    })

    doc
  }

}
