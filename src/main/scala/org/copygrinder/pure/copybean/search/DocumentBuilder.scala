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
import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType}
import play.api.libs.json._

import scala.collection.immutable.ListMap

class DocumentBuilder {

  def buildDocument(copybean: Copybean): Document = {
    val doc = new Document
    doc.add(new IntField("doctype", DocTypes.Copybean.id, Field.Store.NO))

    val idField = new StringField("id", copybean.id, Field.Store.YES)
    doc.add(idField)

    copybean.enforcedTypeIds.foreach(typeId => {
      val typeField = new StringField("enforcedTypeIds", typeId, Field.Store.NO)
      doc.add(typeField)
    })

    addFieldsToDoc(doc, copybean.content, "content")

    doc
  }

  protected def addFieldsToDoc(doc: Document, field: Any, prefix: String): Unit = {

    field match {
      case s: String =>
        doc.add(new TextField(prefix, s, Field.Store.NO))
      case d: Double =>
        doc.add(new DoubleField(prefix, d, Field.Store.NO))
      case i: Int =>
        doc.add(new IntField(prefix, i, Field.Store.NO))
      case bool: Boolean =>
        doc.add(new StringField(prefix, bool.toString, Field.Store.NO))
      case array: Seq[_] =>
        array.foreach(arrayValue => {
          addFieldsToDoc(doc, arrayValue, prefix)
        })
      case map: ListMap[_, _] =>
        map.foreach(value => {
          addFieldsToDoc(doc, value._2, prefix + "." + value._1)
        })
      case u: JsUndefined =>
    }

  }

  def buildDocument(copybeanType: CopybeanType): Document = {
    implicit val doc = new Document
    doc.add(new IntField("doctype", DocTypes.CopybeanType.id, Field.Store.NO))
    doc.add(new TextField("id", copybeanType.id, Field.Store.YES))
    doc.add(new TextField("cardinality", copybeanType.cardinality.toString, Field.Store.NO))

    addOption("displayName", copybeanType.displayName)
    addOption("beanDescFormat", copybeanType.instanceNameFormat)

    copybeanType.fields.map(_.foreach { fieldDef =>
      doc.add(new TextField("fieldDef.id", fieldDef.id, Field.Store.NO))
      doc.add(new TextField("fieldDef." + fieldDef.id + ".type", fieldDef.`type`.toString, Field.Store.NO))
    })

    copybeanType.tags.map(_.foreach { tag =>
      doc.add(new TextField("tags", tag, Field.Store.NO))
    })

    doc
  }


  protected def addOption(id: String, value: Option[String])(implicit doc: Document) = {
    if (value.isDefined) {
      doc.add(new TextField(id, value.get, Field.Store.NO))
    }
  }

}

object DocTypes extends Enumeration {

  type DocTypes = Value

  val Copybean, CopybeanType = Value

}