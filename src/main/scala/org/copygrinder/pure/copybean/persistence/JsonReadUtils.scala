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
package org.copygrinder.pure.copybean.persistence

import org.copygrinder.pure.copybean.exception.JsonInputException
import play.api.libs.json._

import scala.collection.{Seq, mutable}


trait JsonReadUtils extends DefaultReads {

  protected def enumReads[E <: Enumeration](enum: E): Reads[E#Value] = new Reads[E#Value] {
    def reads(json: JsValue): JsResult[E#Value] = json match {
      case JsString(s) => {
        try {
          JsSuccess(enum.withName(s))
        } catch {
          case _: NoSuchElementException => JsError(s"Enumeration expected of type: '${ enum.getClass }', but it does not appear to contain the value: '$s'")
        }
      }
      case _ => JsError("String value expected")
    }
  }

  implicit def trackingMapReads[V](implicit fmtv: Reads[V]): Reads[Map[String, V]] = new Reads[Map[String, V]] {
    def reads(json: JsValue): JsResult[Map[String, V]] = {
      json match {
        case o: JsObjectWrapper => o.objectUnreadFields.clear()
        case _ =>
      }
      mapReads(fmtv).reads(json)
    }
  }

  def readWrapper[T](r: Reads[T]): Reads[T] = {
    new Reads[T] {
      override def reads(jsValue: JsValue): JsResult[T] = {
        val wrapped = wrap(jsValue, "")
        val result = r.reads(wrapped)
        result match {
          case _: JsSuccess[_] => {
            check(wrapped)
          }
          case _ =>
        }
        result
      }
    }
  }

  def wrap(jsValue: JsValue, prefix: String = "."): JsValue = {
    jsValue match {
      case o: JsObject => {
        val fields = o.fields.map(field => {
          (field._1, wrap(field._2))
        })
        new JsObjectWrapper(fields, prefix)
      }
      case a: JsArray => {
        val values = a.value.map(value => {
          wrap(value)
        })
        new JsArrayWrapper(values)
      }
      case j: JsValue => j
    }
  }

  def check(jsValue: JsValue): Unit = {
    jsValue match {
      case rt: ReadTracking => {
        if (rt.unreadFields.nonEmpty) {
          val label = if (rt.unreadFields.size > 1) "fields" else "field"
          throw new JsonInputException(s"Unknown $label: " + rt.unreadFields.mkString(", "))
        }
      }
      case _ =>
    }
  }

}

trait ReadTracking {

  def unreadFields: Set[String]

}

class JsObjectWrapper(fields: Seq[(String, JsValue)], prefix: String) extends JsObject(fields) with ReadTracking {

  val objectUnreadFields = new mutable.HashSet[String]()

  fields.map(_._1).foreach(objectUnreadFields.add(_))

  override def unreadFields: Set[String] = {

    val nestedFields = fields.flatMap(field => {
      field._2 match {
        case r: ReadTracking => r.unreadFields.map(field._1 + prefix + _)
        case _ => None
      }
    })

    objectUnreadFields.toSet ++ nestedFields
  }


  override def \(fieldName: String): JsValue = {
    objectUnreadFields.remove(fieldName)
    super.\(fieldName)
  }

}

class JsArrayWrapper(value: Seq[JsValue]) extends JsArray(value) with ReadTracking {

  override def unreadFields: Set[String] = {

    val nestedFields = value.zipWithIndex.foldLeft(Set[String]())((result, value) => {
      value._1 match {
        case r: ReadTracking => {
          result ++ r.unreadFields.map(field => "[" + value._2 + "]." + field)
        }
        case _ => result
      }
    })

    nestedFields
  }


}
