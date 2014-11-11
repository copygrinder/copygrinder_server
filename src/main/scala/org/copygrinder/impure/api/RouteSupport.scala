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
package org.copygrinder.impure.api


import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.system.{SiloScope, SiloScopeFactory}
import play.api.libs.json._
import spray.http.HttpRequest
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling.{Deserialized, FromRequestUnmarshaller, MalformedContent}
import spray.routing._

import scala.concurrent.{Future, _}


trait RouteSupport extends Directives with PlayJsonSupport with LazyLogging with CorsSupport {

  val siloScopeFactory: SiloScopeFactory

  implicit def executionContext: ExecutionContext

  protected implicit def unmarshaller[T](implicit r: Reads[T]) = new FromRequestUnmarshaller[T] {
    override def apply(req: HttpRequest): Deserialized[T] = {
      val json = Json.parse(req.entity.data.toByteArray)
      r.reads(json) match {
        case s: JsSuccess[T] => Right(s.get)
        case e: JsError => Left(new MalformedContent(JsError.toFlatJson(e).toString()))
      }
    }
  }

  def scopedComplete[T](siloId: String)(body: => (SiloScope) => T)(implicit w: Writes[T]): StandardRoute = {
    lazy val siloScope = siloScopeFactory.build(siloId)
    futureComplete(Json.toJson(body(siloScope)))
  }

  def futureComplete[T]: (=> ToResponseMarshallable) => StandardRoute = (marshallable) => {
    complete(
      Future {
        marshallable
      }
    )
  }

}
