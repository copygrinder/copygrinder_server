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

import spray.http.HttpHeaders._
import spray.http.HttpMethods._
import spray.http.{AllOrigins, HttpMethods, HttpResponse}
import spray.routing._

trait CorsSupport {
  this: Directives =>

  protected final val `24HoursInSeconds` = 60 * 60 * 24

  protected val allowOriginHeader = `Access-Control-Allow-Origin`(AllOrigins)

  def cors[T]: Directive0 = mapRequestContext {
    ctx => ctx.withHttpResponseHeadersMapped { headers =>
      allowOriginHeader :: headers
    }
  }

  protected val optionsCorsHeaders = List(
    `Access-Control-Allow-Headers`(
      "Origin, X-Requested-With, Content-Type, Accept, Accept-Encoding, Accept-Language, Host, Referer, User-Agent"),
    `Access-Control-Max-Age`(`24HoursInSeconds`)
  )

  def innerCors[T]: Directive0 = mapRequestContext {
    ctx => ctx.withRouteResponseHandling({
      case Rejected(x) if ctx.request.method.equals(HttpMethods.OPTIONS)
       && x.exists(_.isInstanceOf[MethodRejection]) => {
        ctx.complete(HttpResponse().withHeaders(
          `Access-Control-Allow-Methods`(OPTIONS, GET, POST, PUT, DELETE) :: optionsCorsHeaders
        ))
      }
    })
  }
}