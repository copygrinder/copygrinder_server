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

  protected def addFieldsToDoc(doc: Document, jValue: JsValue, prefix: String): Unit = {

    jValue match {
      case s: JsString =>
        doc.add(new TextField(prefix, s.value, Field.Store.NO))
      case dec: JsNumber =>
        doc.add(new DoubleField(prefix, dec.value.toDouble, Field.Store.NO))
      case bool: JsBoolean =>
        doc.add(new StringField(prefix, bool.value.toString, Field.Store.NO))
      case JsNull =>
        doc.add(new StringField(prefix, "null", Field.Store.NO))
      case array: JsArray =>
        array.value.foreach(arrayValue => {
          addFieldsToDoc(doc, arrayValue, prefix)
        })
      case jObj: JsObject =>
        jObj.value.foreach(value => {
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