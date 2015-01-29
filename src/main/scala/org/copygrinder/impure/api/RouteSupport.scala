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


import akka.actor.ActorContext
import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.system.{SiloScope, SiloScopeFactory}
import play.api.libs.json._
import shapeless.{::, HNil}
import spray.http.HttpRequest
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling.{Deserialized, FromRequestUnmarshaller, MalformedContent}
import spray.routing._

import scala.concurrent.{Future, _}


trait RouteSupport extends Directives with PlayJsonSupport with LazyLogging with CorsSupport {

  val siloScopeFactory: SiloScopeFactory

  implicit def executionContext: ExecutionContext = ac.dispatcher

  implicit def ac: ActorContext

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

  protected val siloPathPartial = pathPrefix(Segment)

  protected val siloPath = path(Segment)

  protected val copybeansPathPartial = siloPathPartial & pathPrefix("copybeans")

  protected val copybeansPath = siloPathPartial & path("copybeans")

  protected val copybeansIdPath = copybeansPathPartial & path(Segment)

  protected val copybeansTypesPathPartial = copybeansPathPartial & pathPrefix("types")

  protected val copybeansTypesPath = copybeansPathPartial & path("types")

  protected val copybeansTypeIdPath = copybeansTypesPathPartial & path(Segment)

  protected val filePath = siloPathPartial & path("files")

  protected val copybeansIdFieldPath = copybeansPathPartial & pathPrefix(Segment) & path(Segment)

  protected val passwordPath = siloPathPartial & path("password")

  protected object BuildRoute {

    def apply(path: Directive[::[String, HNil]]): OneArgRouteBuilder = {
      new OneArgRouteBuilder(path)
    }

    def apply[S](path: Directive[::[String, ::[S, HNil]]]): TwoArgRouteBuilder[S] = {
      new TwoArgRouteBuilder(path, (s: S) => false)
    }

    def apply[S, Q](path: Directive[::[String, ::[S, ::[Q, HNil]]]]): ThreeArgRouteBuilder[S, Q] = {
      new ThreeArgRouteBuilder(path, (s: S, q: Q) => false)
    }
  }

  protected class OneArgRouteBuilder(path: Directive[::[String, HNil]]) {

    def apply[R](func: (SiloScope) => R)(implicit w: Writes[R]): Route = {
      path { (siloId) =>
        scopedComplete(siloId) { siloScope =>
          func(siloScope)
        }
      }
    }

    protected def paramsShouldReject(params: Seq[(String, String)]) = {
      false
    }

    def withParams[R](func: (SiloScope) => (Seq[(String, String)]) => R)(implicit w: Writes[R]): Route = {
      new TwoArgRouteBuilder(path & parameterSeq, paramsShouldReject).apply(func)
    }
  }

  protected class TwoArgRouteBuilder[S](path: Directive[::[String, ::[S, HNil]]], shouldReject: (S) => Boolean) {
    def apply[R](func: (SiloScope) => (S) => R)(implicit w: Writes[R]): Route = {
      path { (siloId, secondValue) =>
        if (shouldReject(secondValue)) {
          reject
        } else {
          scopedComplete(siloId) { siloScope =>
            func(siloScope)(secondValue)
          }
        }
      }
    }
  }

  protected class ThreeArgRouteBuilder[S, Q](path: Directive[::[String, ::[S, ::[Q, HNil]]]], shouldReject: (S, Q) => Boolean) {
    def apply[R](func: (SiloScope) => (S, Q) => R)(implicit w: Writes[R]): Route = {
      path { (siloId, secondValue, thirdValue) =>
        if (shouldReject(secondValue, thirdValue)) {
          reject
        } else {
          scopedComplete(siloId) { siloScope =>
            func(siloScope)(secondValue, thirdValue)
          }
        }
      }
    }
  }

}
