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
import org.copygrinder.pure.copybean.model.ReifiedField._
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.model.{MergeData, BeanDelta}
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

  // scalastyle:off cyclomatic.complexity
  protected def convertAny(value: Any): JsValue = {
    value match {
      case b: Boolean => JsBoolean(b)
      case i: Int => JsNumber(i)
      case long: Long => JsNumber(long)
      case dec: BigDecimal => JsNumber(dec.toLong)
      case s: String => JsString(s)
      case m: ListMap[_, _] => convertListMap(m)
      case seq: Iterable[_] => convertList(seq)
      case null => JsNull //scalastyle:ignore
      case x => throw new JsonWriteException(s"Can't write JSON for value '$x' with class '${x.getClass}'")
    }
  }

  protected def convertList(list: Iterable[Any]): JsArray = {
    if (list.nonEmpty) {
      val head = list.head
      head match {
        case _: String =>
          traversableWrites[String].writes(list.asInstanceOf[Iterable[String]])
        case _ => if (list.forall(_.isInstanceOf[Int])) {
          traversableWrites[Int].writes(list.asInstanceOf[Iterable[Int]])
        } else if (list.exists(_.isInstanceOf[BigDecimal])) {
          traversableWrites[BigDecimal].writes(list.map {
            case int: Int => BigDecimal(int)
            case dec: BigDecimal => dec
          })
        } else if (list.head.isInstanceOf[Boolean]) {
          traversableWrites[Boolean].writes(list.asInstanceOf[Iterable[Boolean]])
        } else {
          head match {
            case _: Map[_, _] =>
              val newList = list.asInstanceOf[Iterable[Map[String, Any]]].map(map => {
                val newMap = map.map(entry => {
                  entry._1 -> entry._2
                })
                stringAnyMapToJsObject(newMap)
              })
              JsArray(newList.toSeq)
            case _ =>
              throw new JsonWriteException(s"Can't write JSON for list with value '$head")
          }
        }
      }
    } else {
      JsArray()
    }
  } // scalastyle:on cyclomatic.complexity

  protected def convertListMap(m: ListMap[_, Any]): JsObject = {
    if (m.nonEmpty) {
      val head = m.head._1
      head match {
        case _: String =>
          stringAnyMapToJsObject(m.asInstanceOf[Map[String, Any]])
        case _ =>
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

  protected def reifiedFieldWritesMap(fields: ListMap[String, ReifiedField], bean: Copybean) = {

    val jsValues = fields.values.map(field => {
      (field.fieldDef.id, reifiedFieldWrites(field, bean))
    })

    JsObject(jsValues.toSeq)
  }

  def reifiedFieldWrites(field: ReifiedField, bean: Copybean): JsValue = {
    field match {
      case r: ReferenceReifiedField =>
        val jsValue = Json.obj(
          "ref" -> r.castVal
        )
        r.refBean.fold(jsValue) { refBean =>
          jsValue +("expand", copybeanWrites.writes(refBean))
        }
      case f: FileOrImageReifiedField =>
        convertAny(f.value).as[JsObject] +
         ("url", JsString(s"copybeans/${bean.id}/${f.fieldDef.id}"))
      case list: ListReifiedField =>
        val listJsVals = list.castVal.map(listField => {
          reifiedFieldWrites(listField, bean)
        })
        JsArray(listJsVals)
      case f: ReifiedField => convertAny(f.value)
    }
  }


  implicit val copybeanWrites: Writes[Copybean] = new Writes[Copybean] {
    override def writes(copybean: Copybean): JsValue = {
      copybean match {
        case c: CopybeanImpl =>
          val w = Json.writes[CopybeanImpl]
          w.writes(c)
        case rc: ReifiedCopybeanImpl =>
          reifiedCopybeanWrites.writes(rc)
        case unknown => throw new RuntimeException("Unhandled copybean type: " + unknown)
      }
    }
  }

  implicit val fieldTypeWrites = enumWrites(FieldType)

  implicit val copybeanValidatorDefWrites = Json.writes[CopybeanFieldValidatorDef]

  implicit val copybeanFieldDefWrites = Json.writes[CopybeanFieldDef]

  implicit val cardinalityWrites = enumWrites(Cardinality)

  implicit val mergeDataWrites = Json.writes[MergeData]

  implicit val commitWrites = new Writes[Commit] {
    override def writes(c: Commit): JsValue = {
      val out = Json.obj(
        "id" -> c.id,
        "branchId" -> c.branchId,
        "parentCommitId" -> c.parentCommitId,
        "author" -> c.author
      )

      c.mergeData.fold(out) { mergeData =>
        out + ("merge" -> mergeDataWrites.writes(mergeData))
      }

    }
  }


  implicit val reifiedCopybeanWrites: Writes[ReifiedCopybeanImpl] = new Writes[ReifiedCopybeanImpl] {
    def writes(bean: ReifiedCopybeanImpl): JsValue = {
      Json.obj(
        "id" -> bean.id,
        "enforcedTypeIds" -> bean.enforcedTypeIds,
        "content" -> reifiedFieldWritesMap(bean.reifiedFields, bean),
        "names" -> bean.names
      )
    }
  }

  val unreifiedCopybeanWrites: Writes[ReifiedCopybean] = new Writes[ReifiedCopybean] {
    def writes(bean: ReifiedCopybean): JsValue = {
      Json.obj(
        "id" -> bean.id,
        "enforcedTypeIds" -> bean.enforcedTypeIds,
        "content" -> unreifiedFieldWritesMap.writes(bean.reifiedFields)
      )
    }
  }

  val unreifiedFieldWritesMap = new Writes[ListMap[String, ReifiedField]] {
    override def writes(fields: ListMap[String, ReifiedField]) = {

      val jsValues = fields.values.map(field => {
        (field.fieldDef.id, unreifiedFieldWrites.writes(field))
      })

      JsObject(jsValues.toSeq)
    }
  }

  val unreifiedFieldWrites = new Writes[ReifiedField] {
    override def writes(field: ReifiedField): JsValue = {
      field match {
        case r: ReferenceReifiedField =>
          Json.obj(
            "ref" -> r.castVal
          )
        case list: ListReifiedField =>
          val listJsVals = list.castVal.map(listField => {
            writes(listField)
          })
          JsArray(listJsVals)
        case f: ReifiedField => convertAny(f.value)
      }
    }
  }

  implicit val deltaWrites = new Writes[Iterable[BeanDelta]] {
    def writes(deltas: Iterable[BeanDelta]): JsValue = {

      val fieldChanges = deltas.foldLeft(Json.obj()) { (result, delta) =>
        val id = delta.newField.fieldDef.id

        val innerObj = Json.obj()

        val obj = if (delta.oldField.isDefined) {
          innerObj + ("oldValue" -> reifiedFieldWrites(delta.oldField.get, delta.oldBean.get))
        } else {
          innerObj
        }

        val newObj = obj + ("newValue" -> reifiedFieldWrites(delta.newField, delta.newBean))
        result ++ Json.obj(id -> newObj)
      }

      val resultObj = Json.obj("fieldChanges" -> fieldChanges)

      val delta = deltas.head

      if (delta.oldBean.isDefined) {
        val oldTypeIds = delta.oldBean.get.enforcedTypeIds
        val deltaTypes = delta.newBean.enforcedTypeIds.diff(oldTypeIds)
        if (deltaTypes.nonEmpty) {
          resultObj ++ Json.obj(
            "oldEnforcedTypeIds" -> convertAny(delta.newBean.enforcedTypeIds),
            "newEnforcedTypeIds" -> convertAny(delta.newBean.enforcedTypeIds)
          )
        } else {
          resultObj
        }
      } else {
        if (delta.newBean.enforcedTypeIds.nonEmpty) {
          resultObj ++ Json.obj("newEnforcedTypeIds" -> convertAny(delta.newBean.enforcedTypeIds))
        } else {
          resultObj
        }
      }

    }
  }


}
