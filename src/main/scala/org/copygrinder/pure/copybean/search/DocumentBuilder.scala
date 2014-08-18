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
import org.json4s.JsonAST._

class DocumentBuilder {

  def buildDocument(copybean: Copybean): Document = {
    val doc = new Document

    val idField = new StringField("id", copybean.id, Field.Store.YES)
    doc.add(idField)

    copybean.enforcedTypeIds.foreach(typeId => {
      val typeField = new StringField("enforcedTypeIds", typeId, Field.Store.NO)
      doc.add(typeField)
    })

    addFieldsToDoc(doc, copybean.contains, "contains")

    doc
  }

  protected def addFieldsToDoc(doc: Document, jValue: JValue, prefix: String): Unit = {

    jValue match {
      case string: JString =>
        doc.add(new TextField(prefix, string.s, Field.Store.NO))
      case int: JInt =>
        doc.add(new IntField(prefix, int.num.toInt, Field.Store.NO))
      case dec: JDecimal =>
        doc.add(new DoubleField(prefix, dec.num.toDouble, Field.Store.NO))
      case bool: JBool =>
        doc.add(new StringField(prefix, bool.value.toString, Field.Store.NO))
      case double: JDouble =>
        doc.add(new DoubleField(prefix, double.num, Field.Store.NO))
      case JNull =>
        doc.add(new StringField(prefix, "null", Field.Store.NO))
      case array: JArray =>
        array.arr.foreach(arrayValue => {
          addFieldsToDoc(doc, arrayValue, prefix)
        })
      case jObj: JObject =>
        jObj.obj.foreach(value => {
          addFieldsToDoc(doc, value._2, prefix + "." + value._1)
        })
      case JNothing =>
    }

  }
}
