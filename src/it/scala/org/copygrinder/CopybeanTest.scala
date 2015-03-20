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
import com.ning.http.multipart.FilePart
import dispatch.Defaults._
import dispatch._
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

import scala.concurrent.Await
import scala.concurrent.duration._


class CopybeanTest extends IntegrationTestSupport {

  "Copygrinder" should "POST new types" in {

    val json =
      """
        |[{
        |  "id": "testtype1",
        |  "displayName": "TestTypeOne",
        |  "instanceNameFormat": "This bean is named $content.testfield2$ $content.testfield1$.",
        |  "fields": [
        |    {
        |      "id": "testfield1",
        |      "type": "String",
        |      "displayName": "String field",
        |      "validators": [{"type":"required"}]
        |    },{
        |      "id": "testfield2",
        |      "type": "Integer",
        |      "displayName": "Integer field"
        |    },{
        |      "id": "testfield4",
        |      "type": "File",
        |      "displayName": "File field"
        |    },{
        |      "id": "testfield5",
        |      "type": "Image",
        |      "displayName": "Image field"
        |    },{
        |      "id": "testfield6",
        |      "type": "Html",
        |      "displayName": "Html field"
        |    }
        |  ],
        |  "cardinality": "Many"
        |},{
        |  "id": "testtype2",
        |  "displayName": "TestTypeTwo",
        |  "cardinality": "One"
        |}]""".stripMargin

    val req = copybeansTypesUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 2 second)
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
      checkStatus(req, response)
    }

    Await.result(responseFuture, 1 second)
  }

  it should "GET all copybeans" in {

    val req = copybeansUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
      assert(response.getResponseBody.contains("testfield2\":2"))
      assert(response.getResponseBody.contains("testfield2\":4"))
    }

    Await.result(responseFuture, 1 second)
  }

  it should "GET specific copybeans" in {

    val req = copybeansUrl.GET.setQueryParameters(Map("content.testfield1" -> Seq("3")))

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
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
      checkStatus(req, response, 400)
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
      checkStatus(req, response, 400)
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
      checkStatus(req, response)
    }

    Await.result(responseFuture, 1 second)
  }

  it should "search for predefined types" in {

    val req = copybeanTypeIdUrl("classBackedFieldValidator").GET

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
      assert(response.getResponseBody.contains("classBackedFieldValidator"))
    }

    Await.result(responseFuture, 1 second)
  }

  def getId() = {
    val req = copybeansUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
      val json = Json.parse(response.getResponseBody).as[JsArray]
      val bean = json.value.find { bean =>
        val ids = bean.\("enforcedTypeIds").as[JsArray]
        ids.value.contains(JsString("testtype1"))
      }
      bean.get.\("id").as[JsString].value
    }

    val id = Await.result(responseFuture, 1 second)
    id
  }

  it should "edit existing Copybeans" in {

    val req = copybeansUrl.GET

    val id = getId()

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
      checkStatus(req2, response)
    }

    Await.result(responseFuture2, 1 second)

    val responseFuture3 = Http(req).map { response =>
      checkStatus(req, response)
      assert(response.getResponseBody.contains("1-edited"))
    }

    Await.result(responseFuture3, 1 second)
  }

  it should "edit existing Copybean Types" in {

    val json =
      """
        |{
        |  "id": "testtype2",
        |  "displayName": "TestTypeTwo-Edited",
        |  "cardinality": "One",
        |  "instanceNameFormat": "$displayName$"
        |}""".stripMargin

    val req = copybeanTypeIdUrl("testtype2").PUT.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 1 second)

    val req2 = copybeansTypesUrl.GET.setQueryParameters(Map("id" -> Seq("testtype2")))

    val responseFuture2 = Http(req2).map { response =>
      checkStatus(req2, response)
      assert(response.getResponseBody.contains("TestTypeTwo-Edited"))
    }

    Await.result(responseFuture2, 1 second)
  }

  it should "handle File fields" in {

    val file = new File("developer.md")

    val req = filesUrl.POST.addBodyPart(
      new FilePart("upload", file, "text/x-markdown", "UTF-8")
    ).setHeader("Transfer-Encoding", "chunked")

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
      getHash(response)
    }

    val hash = Await.result(responseFuture, 1 second)

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "testtype1"
        |  ],
        |  "content": {
        |    "testfield1": "abc",
        |    "testfield4": {
        |      "filename": "developer.md",
        |      "hash": "%s"
        |    }
        |  }
        |}""".stripMargin.format(hash)

    val req2 = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val responseFuture2 = Http(req2).map { response =>
      checkStatus(req2, response)
      val body = response.getResponseBody
      body.substring(1, body.length - 1)
    }

    val id = Await.result(responseFuture2, 2 second)

    val req3 = copybeanFileUrl(id, "testfield4").GET

    val responseFuture3 = Http(req3).map { response =>
      checkStatus(req3, response)
      val body = response.getResponseBody
      assert(body.length > 2 * 1024)
    }

    Await.result(responseFuture3, 1 second)
  }

  protected def getHash(response: Response) = {
    val json = Json.parse(response.getResponseBody).as[JsArray]
    json.value.seq(0).\("content").as[JsObject].\("hash").as[JsString].value
  }

  it should "handle Image fields" in {

    val file = new File("src/it/resources/test.jpg")

    val req = filesUrl.POST.addBodyPart(
      new FilePart("upload", file, "image/jpeg", null)
    ).setHeader("Transfer-Encoding", "chunked")

    val hash = doReqThen(req) { response =>
      getHash(response)
    }

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "testtype1"
        |  ],
        |  "content": {
        |    "testfield1": "abc",
        |    "testfield5": {
        |      "filename": "test.jpg",
        |      "hash": "%s"
        |    }
        |  }
        |}""".stripMargin.format(hash)

    val req2 = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    val id = doReqThen(req2) { response =>
      val body = response.getResponseBody
      body.substring(1, body.length - 1)
    }

    val req3 = copybeanFileUrl(id, "testfield5").GET

    doReqThen(req3) { response =>
      val body = response.getResponseBody
      assert(body.length == 30128)
    }
  }

  it should "handle lists" in {

    val json =
      """
        |{
        |  "id": "listtype",
        |  "displayName": "List Example",
        |  "instanceNameFormat": "List type",
        |  "fields":
        |    [{
        |      "id": "stringlist",
        |      "type": "List",
        |      "displayName": "String List field",
        |      "attributes": {
        |        "listType": "String"
        |      }
        |    },{
        |      "id": "intlist",
        |      "type": "List",
        |      "displayName": "Integer List field",
        |      "attributes": {
        |        "listType": "Integer"
        |      }
        |    },{
        |      "id": "longlist",
        |      "type": "List",
        |      "displayName": "Long List field",
        |      "attributes": {
        |        "listType": "Long"
        |      }
        |    },{
        |      "id": "filelist",
        |      "type": "List",
        |      "displayName": "File List field",
        |      "attributes": {
        |        "listType": "File"
        |      }
        |    },{
        |      "id": "htmllist",
        |      "type": "List",
        |      "displayName": "Html List field",
        |      "attributes": {
        |        "listType": "Html"
        |      }
        |    }
        |  ],
        |  "cardinality": "Many"
        |}""".stripMargin

    val req = copybeansTypesUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    doReq(req)

    val json2 =
      """
        |{
        |  "enforcedTypeIds": [
        |    "listtype"
        |  ],
        |  "content": {
        |    "stringlist": ["Lorem Ipsum", "123"],
        |    "intlist": [456, 789],
        |    "longlist": [456, 9223372036854775807],
        |    "filelist": [{
        |      "filename": "test2.jpg",
        |      "hash": "abc"
        |    },{
        |      "filename": "test3.jpg",
        |      "hash": "abc"
        |    }],
        |    "htmllist": ["<b>hello</b>", "world"]
        |  }
        |}""".stripMargin

    val req2 = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json2)

    doReq(req2)
  }

}