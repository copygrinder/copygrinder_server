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
package org.copygrinder.unpure.api

import org.copygrinder.pure.copybean.model.Copybean
import org.json4s.{DefaultFormats, Formats}
import spray.httpx.Json4sJacksonSupport
import spray.routing._
import org.json4s.jackson.JsonMethods._

trait CopygrinderApi extends HttpService with Json4sJacksonSupport {

  override implicit def json4sJacksonFormats: Formats = DefaultFormats

  val rootRoute = path("") {
    get {
      complete {
        val jsonValues = parse("""{"name":"joe","age":15}""")
        new Copybean("bean1", Set("hi"), jsonValues)
      }
    }
  }

  val copygrinderRoutes = rootRoute
}