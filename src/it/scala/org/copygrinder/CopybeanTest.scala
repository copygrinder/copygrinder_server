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

import dispatch.Defaults._
import dispatch._
import org.apache.commons.io.FileUtils
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


class CopybeanTest extends FlatSpec with Matchers {

  val siloId = "integrationtest"

  val wiring = TestWiring.wiring

  "Copygrinder" should "create a new silo and create a type" in {

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)

    FileUtils.deleteDirectory(siloDir)

    val json =
      """
        |{
        |  "id":"page3",
        |  "singularTypeNoun":"Page",
        |  "beanDescFormat": "name",
        |  "fieldDefs": [],
        |  "validators": []
        |}""".stripMargin

    val copybeansReq = url(s"http://localhost:9999/$siloId/copybeans/types")
      .POST.setContentType("application/json", "UTF8")
      .setBody(json)

    val responseFuture = Http(copybeansReq).map { response =>

      println("RESPONSE " + response.getResponseBody)

      assert(response.getStatusCode == 200)

      assert(siloDir.exists)
    }

    Await.result(responseFuture, 1 second)

  }

}