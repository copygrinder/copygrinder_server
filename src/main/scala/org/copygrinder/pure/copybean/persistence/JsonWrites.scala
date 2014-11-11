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
import play.api.libs.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait JsonWrites extends DefaultWrites {

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

  implicit val copybeanWrites = new Writes[Copybean] {
    override def writes(copybean: Copybean): JsValue = {
      copybean match {
        case c: CopybeanImpl => {
          val w = Json.writes[CopybeanImpl]
          w.writes(c)
        }
        case rc: ReifiedCopybeanImpl => {
          val w = Json.writes[ReifiedCopybeanImpl]
          w.writes(rc)
        }
        case unknown => throw new RuntimeException("Unhandled copybean type: " + unknown)
      }
    }
  }


  implicit val fieldTypeWrites = enumWrites(FieldType)

  implicit val copybeanFieldDefWrites = Json.writes[CopybeanFieldDef]

  implicit val copybeanValidatorDefWrites = Json.writes[CopybeanValidatorDef]

  implicit val cardinalityWrites = enumWrites(Cardinality)

  implicit val copybeanTypeWrites = Json.writes[CopybeanType]

  implicit val ReifiedCopybeanWrites = Json.writes[ReifiedCopybeanImpl]


}
