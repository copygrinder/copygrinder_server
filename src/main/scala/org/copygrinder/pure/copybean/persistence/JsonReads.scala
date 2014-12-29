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

import org.copygrinder.pure.copybean.model._
import play.api.data.validation.ValidationError
import play.api.libs.functional.syntax._
import play.api.libs.json.Json._
import play.api.libs.json._

import scala.collection.{Map, Seq}
import scala.collection.immutable.ListMap


trait JsonReads extends JsonReadUtils {

  def metaValueToJsValue(value: JsValue): JsResult[Any] = {
    value match {
      case JsBoolean(b) => JsSuccess(b)
      case JsNumber(n) => {
        if (n.isValidInt) {
          JsSuccess(n.toInt)
        } else {
          JsSuccess(n)
        }
      }
      case JsString(s) => JsSuccess(s)
      case JsArray(arr) => {
        val list = arr.map(metaValueToJsValue)
        val values = list.map(result => result.get).toSeq
        JsSuccess(values)
      }
      case JsObject(m) => {
        val m1 = m.map(f => (f._1, metaValueToJsValue(f._2))).toMap
        JsSuccess(m1)
      }
      case x => JsError(x.toString())
    }
  }

  def listMapReads[V](implicit fmtv: Reads[V]): Reads[ListMap[String, V]] = new Reads[ListMap[String, V]] {
    override def reads(json: JsValue) = {
      json match {
        case o: JsObjectWrapper => o.objectUnreadFields.clear()
        case _ =>
      }
      listMapReadsImpl(json, fmtv)
    }
  }

  /**
   * This function is a slightly modified version of DefaultReads.mapReads to handle ListMaps
   */
  protected def listMapReadsImpl[V](json: JsValue, fmtv: Reads[V]):JsResult[ListMap[String, V]] = {
    json match {
      case JsObject(m) => {

        type Errors = Seq[(JsPath, Seq[ValidationError])]
        def locate(e: Errors, key: String):Errors = e.map { case (p, valerr) => (JsPath \ key) ++ p -> valerr}

        m.foldLeft(Right(ListMap.empty): Either[Errors, ListMap[String, V]]) {
          case (acc, (key, value)) => (acc, fromJson[V](value)(fmtv)) match {
            case (Right(vs), JsSuccess(v, _)) => Right(vs + (key -> v))
            case (Right(_), JsError(e)) => Left(locate(e, key))
            case (Left(e), _: JsSuccess[_]) => Left(e)
            case (Left(e1), JsError(e2)) => Left(e1 ++ locate(e2, key))
          }
        }.fold(JsError.apply, res => JsSuccess(res))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.jsobject"))))
    }
  }


  val anyReads = Reads[Any](m => metaValueToJsValue(m))

  implicit val readsMap = Reads[ListMap[String, Any]](m => listMapReads[Any](anyReads).reads(m))

  implicit val fieldTypeReads = enumReads(FieldType)

  implicit val copybeanFieldDefReads = Json.reads[CopybeanFieldDef]

  implicit val anonymousCopybeanValidatorDefReads = Json.reads[CopybeanValidatorDef]

  implicit val cardinalityReads = enumReads(Cardinality)

  implicit val copybeanTypeRead = readWrapper(Json.reads[CopybeanType])

  implicit val anonymousCopybeanReads: Reads[AnonymousCopybean] = readWrapper((
   (JsPath \ "enforcedTypeIds").read[Set[String]] and
    (JsPath \ "content").read[ListMap[String, Any]]
   )(AnonymousCopybeanImpl.apply _)
  )


  implicit val copybeanReads = readWrapper(Json.reads[CopybeanImpl])

}
