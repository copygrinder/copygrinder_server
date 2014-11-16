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

import com.ning.http.client.Response
import dispatch.Defaults._
import dispatch._
import org.apache.commons.io.FileUtils
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.{JsArray, JsString, Json}

import scala.concurrent.Await
import scala.concurrent.duration._


class CopybeanTest extends FlatSpec with Matchers {

  val siloId = "integrationtest"

  val wiring = TestWiring.wiring

  val copybeansTypesUrl = url(s"http://localhost:9999/$siloId/copybeans/types")

  val copybeansUrl = url(s"http://localhost:9999/$siloId/copybeans")

  def copybeanIdUrl(id: String) = url(s"http://localhost:9999/$siloId/copybeans/$id")

  def copybeanTypeIdUrl(id: String) = url(s"http://localhost:9999/$siloId/copybeans/types/$id")


  "Copygrinder" should "create a new silo and POST types" in {

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)

    FileUtils.deleteDirectory(siloDir)

    val json =
      """
        |[{
        |  "id": "testtype1",
        |  "singularTypeNoun": "TestTypeOne",
        |  "pluralTypeNoun": "TestTypeOnes",
        |  "instanceNameFormat": "This bean is named $testfield2$ $testfield1$.",
        |  "fields": [{
        |      "id": "testfield1",
        |      "type": "String"
        |    },{
        |      "id": "testfield2",
        |      "type": "Integer"
        |  }],
        |  "validators": [{
        |    "type": "required",
        |    "args": {
        |      "testfield1": "true"
        |    }
        |  }],
        |  "cardinality": "Many"
        |},{
        |  "id": "testtype2",
        |  "singularTypeNoun": "TestTypeTwo",
        |  "cardinality": "One"
        |}]""".stripMargin

    val req = copybeansTypesUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>

      checkStatus(response)

      assert(siloDir.exists)
    }

    Await.result(responseFuture, 1 second)
  }

  it should "POST new copybeans" in {

    val json =
      """
        |[{
        |  "enforcedTypeIds": [
        |    "testtype1"
        |  ],
        |  "content": {
        |    "testfield1":"1",
        |    "testfield2":2
        |  }
        |},{
        |  "enforcedTypeIds": [
        |    "testtype2"
        |  ],
        |  "content": {
        |    "testfield1":"3",
        |    "testfield2":4
        |  }
        |}]""".stripMargin

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
    }

    Await.result(responseFuture, 1 second)
  }

  it should "GET all copybeans" in {

    val req = copybeansUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
      assert(response.getResponseBody.contains("testfield2\":2"))
      assert(response.getResponseBody.contains("testfield2\":4"))
    }

    Await.result(responseFuture, 1 second)
  }

  it should "GET specific copybeans" in {

    val req = copybeansUrl.GET.setQueryParameters(Map("testfield1" -> Seq("3")))

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
      assert(response.getResponseBody.contains("testfield1\":\"3"))
      assert(!response.getResponseBody.contains("testfield2\":2"))
    }

    Await.result(responseFuture, 1 second)
  }

  it should "POST a copybean that fails validation" in {

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "testtype1"
        |  ],
        |  "content": {
        |    "testfield1": ""
        |  }
        |}""".stripMargin

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(response, 400)
      assert(response.getResponseBody.contains("required"))
    }

    Await.result(responseFuture, 1 second)
  }

  it should "reject a Copybean POST that has unknown fields" in {

    val json =
      """
        |{
        |  "enforcedTypeIds": [],
        |  "content": {},
        |  "bogus": "noWay"
        |}""".stripMargin

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(response, 400)
      assert(response.getResponseBody.contains("bogus"))
    }

    Await.result(responseFuture, 1 second)
  }

  it should "accept predefined types" in {

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "copygrinderAdminMetatype"
        |  ],
        |  "content": {
        |    "siloName": "IntegrationTest Website"
        |  }
        |}""".stripMargin

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
    }

    Await.result(responseFuture, 1 second)
  }

  it should "edit existing Copybeans" in {

    val req = copybeansUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
      val json = Json.parse(response.getResponseBody).as[JsArray]
      val bean = json.value.find { bean =>
        val ids = bean.\("enforcedTypeIds").as[JsArray]
        ids.value.contains(JsString("testtype1"))
      }
      bean.get.\("id").as[JsString].value
    }

    val id = Await.result(responseFuture, 1 second)

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "testtype1"
        |  ],
        |  "content": {
        |    "testfield1":"1-edited",
        |    "testfield2":2
        |  }
        |}""".stripMargin

    val req2 = copybeanIdUrl(id).PUT.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture2 = Http(req2).map { response =>
      checkStatus(response)
    }

    Await.result(responseFuture2, 1 second)

    val responseFuture3 = Http(req).map { response =>
      checkStatus(response)
      assert(response.getResponseBody.contains("1-edited"))
    }

    Await.result(responseFuture3, 1 second)
  }

  it should "edit existing Copybean Types" in {

    val json =
      """
        |{
        |  "id": "testtype2",
        |  "singularTypeNoun": "TestTypeTwo-Edited",
        |  "cardinality": "One"
        |}""".stripMargin

    val req = copybeanTypeIdUrl("testtype2").PUT.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(response)
    }

    Await.result(responseFuture, 1 second)

    val req2 = copybeansTypesUrl.GET.setQueryParameters(Map("id" -> Seq("testtype2")))

    val responseFuture2 = Http(req2).map { response =>
      checkStatus(response)
      assert(response.getResponseBody.contains("TestTypeTwo-Edited"))
    }

    Await.result(responseFuture2, 1 second)
  }

  def checkStatus(response: Response, code: Int = 200) = {
    val status = response.getStatusCode
    if (status != code) {
      println("RESPONSE: " + response.getResponseBody)
      assert(status == code)
    }
  }

}