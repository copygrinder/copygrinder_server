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

import org.copygrinder.pure.copybean.exception.JsonWriteException
import org.copygrinder.pure.copybean.model.ReifiedField.{FileOrImageReifiedField, ReifiedFieldSupport}
import org.copygrinder.pure.copybean.model._
import play.api.libs.json._

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait JsonWrites extends DefaultWrites {

  protected def stringAnyMapToJsObject(map: Map[String, Any]): JsObject = {
    val fields = map.map(entry => {
      val (key, value) = entry
      val jsValue: JsValue = convertAny(value)
      (key, jsValue)
    }).toSeq
    JsObject(fields)
  }

  protected def convertAny(value: Any): JsValue = {
    value match {
      case b: Boolean => JsBoolean(b)
      case i: Int => JsNumber(i)
      case long: Long => JsNumber(long)
      case s: String => JsString(s)
      case m: ListMap[_, _] => {
        convertListMap(m)
      }
      case list: List[_] => {
        convertList(list)
      }
      case field: ReifiedField with ReifiedFieldSupport => {
        reifiedFieldWrites.writes(field)
      }
      case null => JsNull //scalastyle:ignore
      case x => throw new JsonWriteException(s"Can't write JSON for value '$x' with class '${x.getClass}'")
    }
  }

  protected def convertList(list: List[Any]): JsArray = {
    if (!list.isEmpty) {
      val head = list.head
      if (head.isInstanceOf[String]) {
        traversableWrites[String].writes(list.asInstanceOf[List[String]])
      } else if (list.forall(_.isInstanceOf[Int])) {
        traversableWrites[Int].writes(list.asInstanceOf[List[Int]])
      } else if (list.exists(_.isInstanceOf[BigDecimal])) {
        traversableWrites[BigDecimal].writes(list.map(value => {
          value match {
            case int: Int => BigDecimal(int)
            case dec: BigDecimal => dec
          }
        }))
      } else if (head.isInstanceOf[Map[_, _]]) {
        val newList = list.asInstanceOf[List[Map[String, Any]]].map(map => {
          val newMap = map.map(entry => {
            (entry._1 -> entry._2)
          })
          stringAnyMapToJsObject(newMap)
        })
        JsArray(newList)
      } else if (head.isInstanceOf[ReifiedField]) {
        traversableWrites[ReifiedField].writes(list.asInstanceOf[List[ReifiedField]])
      } else {
        throw new JsonWriteException(s"Can't write JSON for list with value '$head")
      }
    } else {
      JsArray()
    }
  }

  protected def convertListMap(m: ListMap[_, Any]): JsObject = {
    if (!m.isEmpty) {
      val head = m.head._1
      if (head.isInstanceOf[String]) {
        stringAnyMapToJsObject(m.asInstanceOf[Map[String, Any]])
      } else {
        throw new JsonWriteException(s"Can't write JSON for map with value '$head")
      }
    } else {
      JsObject(Seq())
    }
  }

  implicit val stringAnyWrites = new Writes[ListMap[String, Any]] {
    override def writes(map: ListMap[String, Any]): JsValue = {
      stringAnyMapToJsObject(map)
    }
  }


  def enumWrites[E <: Enumeration](enum: E): Writes[E#Value] = new Writes[E#Value] {
    def writes(v: E#Value): JsValue = JsString(v.toString)
  }

  implicit def futureWrites[T](implicit w: Writes[T]): Writes[Future[T]] = {
    new Writes[Future[T]] {
      override def writes(future: Future[T]): JsValue = {
        val value = Await.result(future, 5 seconds)
        w.writes(value)
      }
    }
  }

  implicit val reifiedFieldWrites = new Writes[ReifiedField] {
    override def writes(field: ReifiedField): JsValue = {
      convertAny(field.value)
    }
  }

  implicit val copybeanWrites = new Writes[Copybean] {
    override def writes(copybean: Copybean): JsValue = {
      copybean match {
        case c: CopybeanImpl => {
          val w = Json.writes[CopybeanImpl]
          w.writes(c)
        }
        case rc: ReifiedCopybeanImpl => {
          reifiedCopybeanWrites.writes(rc)
        }
        case unknown => throw new RuntimeException("Unhandled copybean type: " + unknown)
      }
    }
  }

  implicit val fieldTypeWrites = enumWrites(FieldType)

  implicit val copybeanValidatorDefWrites = Json.writes[CopybeanFieldValidatorDef]

  implicit val copybeanFieldDefWrites = Json.writes[CopybeanFieldDef]

  implicit val cardinalityWrites = enumWrites(Cardinality)

  implicit val copybeanTypeWrites = Json.writes[CopybeanType]

  implicit val reifiedCopybeanWrites: Writes[ReifiedCopybeanImpl] = new Writes[ReifiedCopybeanImpl] {
    def writes(bean: ReifiedCopybeanImpl): JsValue = {
      Json.obj(
        "id" -> bean.id,
        "enforcedTypeIds" -> bean.enforcedTypeIds,
        "content" -> bean.fields,
        "names" -> bean.names
      )
    }
  }

  implicit val fileMetadataWrites = Json.writes[FileMetadata]

}
