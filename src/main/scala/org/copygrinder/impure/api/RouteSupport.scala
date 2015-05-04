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
import org.copygrinder.impure.copybean.controller.SecurityController
import org.copygrinder.impure.system.{SiloScope, SiloScopeFactory}
import play.api.libs.json._
import shapeless.{::, HNil}
import spray.http.{Uri, HttpRequest}
import spray.httpx.PlayJsonSupport
import spray.httpx.marshalling.ToResponseMarshallable
import spray.httpx.unmarshalling.{Deserialized, FromRequestUnmarshaller, MalformedContent}
import spray.routing._
import spray.routing.authentication.UserPass

import scala.concurrent.{Future, _}


trait RouteSupport extends Directives with PlayJsonSupport with LazyLogging {

  val siloScopeFactory: SiloScopeFactory

  implicit def executionContext: ExecutionContext = ac.dispatcher

  implicit def ac: ActorContext

  val securityController: SecurityController

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

  protected val end = parameterMultiMap & pathEndOrSingleSlash

  protected val siloPathPartial = pathPrefix(Segment)

  protected val siloPath = siloPathPartial & end

  protected val copybeansPathPartial = siloPathPartial & pathPrefix("copybeans")

  protected val copybeansPath = copybeansPathPartial & end

  protected val copybeansIdPath = copybeansPathPartial & pathPrefix(Segment) & end

  protected val copybeansTypesPathPartial = siloPathPartial & pathPrefix("types")

  protected val copybeansTypesPath = copybeansTypesPathPartial & end

  protected val copybeansTypeIdPath = copybeansTypesPathPartial & pathPrefix(Segment) & end

  protected val filePath = siloPathPartial & pathPrefix("files") & end

  protected val copybeansIdFieldPath = copybeansPathPartial & pathPrefix(Segment) & pathPrefix(Segment) & end

  protected val passwordPath = siloPathPartial & pathPrefix("password") & end

  protected val adminPathPartial = siloPathPartial & pathPrefix("admin")

  protected val adminPath = adminPathPartial & end

  protected val branchesPathPartial = siloPathPartial & pathPrefix("branches")

  protected val branchesPath = branchesPathPartial & end

  protected val branchesIdPathPartial = branchesPathPartial & pathPrefix(Segment)

  protected val branchesIdPath = branchesIdPathPartial & end

  protected val branchHeadPath = branchesIdPathPartial & pathPrefix("head") & end

  protected val branchHeadsPath = branchesIdPathPartial & pathPrefix("heads") & end

  protected object BuildRoute {

    def apply(path: Directive[::[String, HNil]]): OneArgRouteBuilder = {
      new OneArgRouteBuilder(path)
    }

    def apply[S](path: Directive[::[String, ::[S, HNil]]]): TwoArgRouteBuilder[S] = {
      new TwoArgRouteBuilder(path)
    }

    def apply[S, Q](path: Directive[::[String, ::[S, ::[Q, HNil]]]]): ThreeArgRouteBuilder[S, Q] = {
      new ThreeArgRouteBuilder(path)
    }

    def apply[S, Q, T](path: Directive[::[String, ::[S, ::[Q, ::[T, HNil]]]]]): FourArgRouteBuilder[S, Q, T] = {
      new FourArgRouteBuilder(path)
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
  }

  protected class TwoArgRouteBuilder[S](path: Directive[::[String, ::[S, HNil]]]) {
    def apply[R](func: (SiloScope) => (S) => R)(implicit w: Writes[R]): Route = {
      path { (siloId, secondValue) =>
        scopedComplete(siloId) { siloScope =>
          func(siloScope)(secondValue)
        }
      }
    }
  }

  protected class ThreeArgRouteBuilder[S, Q](path: Directive[::[String, ::[S, ::[Q, HNil]]]]) {
    def apply[R](func: (SiloScope) => (S, Q) => R)(implicit w: Writes[R]): Route = {
      path { (siloId, secondValue, thirdValue) =>
        scopedComplete(siloId) { siloScope =>
          func(siloScope)(secondValue, thirdValue)
        }
      }
    }
  }

  protected class FourArgRouteBuilder[S, Q, T](path: Directive[::[String, ::[S, ::[Q, ::[T, HNil]]]]]) {
    def apply[R](func: (SiloScope) => (S, Q, T) => R)(implicit w: Writes[R]): Route = {
      path { (siloId, secondValue, thirdValue, fourthValue) =>
        scopedComplete(siloId) { siloScope =>
          func(siloScope)(secondValue, thirdValue, fourthValue)
        }
      }
    }
  }

  protected def authenticator(userPass: Option[UserPass]): Future[Option[String]] =
    Future {
      if (securityController.auth(userPass)) {
        Some(userPass.getOrElse(UserPass("", "")).user.toLowerCase)
      } else {
        None
      }
    }

  protected def hostRoute(route: Route) = {
    hostName {
      host =>
        if (host != "localhost" && host != "127.0.0.1") {
          route.compose(requestContext => {
            val newUri = Uri("/" + host + requestContext.unmatchedPath.toString).path
            requestContext.copy(unmatchedPath = newUri)
          })
        } else {
          reject
        }
    }
  }

}
