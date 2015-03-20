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
package org.copygrinder

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

import com.ning.http.client.Response
import dispatch.Defaults._
import dispatch._
import org.apache.commons.io.FileUtils
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


trait IntegrationTestSupport extends FlatSpec with Matchers {

  val wiring = TestWiring.wiring

  val siloId = "integrationtest"

  val rootUrl = url(s"http://localhost:9999/")

  val baseUrl = rootUrl / siloId

  val copybeansUrl = baseUrl / "copybeans"

  val copybeansTypesUrl = baseUrl / "types"

  def copybeanIdUrl(id: String) = copybeansUrl / id

  def copybeanTypeIdUrl(id: String) = copybeansTypesUrl / id

  val filesUrl = baseUrl / "files"

  def copybeanFileUrl(id: String, field: String) = copybeansUrl / id / field

  def checkStatus(req: Req, response: Response, code: Int = 200) = {
    val status = response.getStatusCode
    if (status != code) {
      println("REQUEST: " + req.toRequest)
      println("REQUEST BODY: " + req.toRequest.getStringData)
      println("RESPONSE: " + response.getResponseBody)
      assert(status == code)
    }
  }

  def doReqThen[T](req: Req, status: Int = 200)(func: (Response) => T): T = {
    val responseFuture = Http(req).map { response =>
      checkStatus(req, response, status)
      func(response)
    }

    Await.result(responseFuture, 1 second)
  }

  def doReq(req: Req): Response = {
    doReqThen(req) { response =>
      response
    }
  }

  def deleteSilo() = {

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)

    FileUtils.deleteDirectory(siloDir)

    val req = rootUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 2 second)

    note("Deleted old silo")
  }

  def initSilo() = {

    val req = baseUrl.POST

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 2 second)

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)
    assert(siloDir.exists)

    note("Initialized new silo")
  }

  Bootstrap.bootstrap(this)
}

object Bootstrap {

  val hasInit = new AtomicBoolean(false)

  def bootstrap(it: IntegrationTestSupport) = {
    if (hasInit.compareAndSet(false, true)) {
      it.deleteSilo()
      it.initSilo()
    }
  }

}
