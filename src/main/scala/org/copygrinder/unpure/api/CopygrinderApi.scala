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

import com.softwaremill.macwire.MacwireMacros._
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.copygrinder.unpure.copybean.CopybeanFactory
import org.copygrinder.unpure.copybean.persistence.PersistenceService
import org.json4s.jackson.JsonMethods._
import org.json4s.{DefaultFormats, Formats}
import spray.http.HttpResponse
import spray.httpx.Json4sJacksonSupport
import spray.routing._


trait CopygrinderApi extends HttpService with Json4sJacksonSupport {

  lazy val persistenceService = wire[PersistenceService]
  lazy val copybeanFactory = wire[CopybeanFactory]

  override implicit def json4sJacksonFormats: Formats = DefaultFormats

  val rootRoute = path("") {
    get {
      complete {
        val jsonValues = parse( """{"name":"joe","age":15}""")
        new Copybean(("bean1"), Set("hi"), jsonValues)
      }
    }
  }

  val copybeanRoute = path("copybeans") {
    post {
      entity(as[AnonymousCopybean]) { bean =>
        complete {
          val copybean = copybeanFactory.create(bean)
          persistenceService.store(copybean)
          copybean.id
        }
      }
    }
  }

  val copygrinderRoutes = rootRoute ~ copybeanRoute
}