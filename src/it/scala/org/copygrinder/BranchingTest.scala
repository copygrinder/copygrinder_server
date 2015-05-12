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

import org.copygrinder.framework.IntegrationTestSupport
import play.api.libs.json.{JsObject, JsArray, JsString, Json}

import scala.concurrent.Await


class BranchingTest extends IntegrationTestSupport {

  "Copygrinder Branch Handling" should "POST new branch types" in {

    val json =
      """
        |{
        |  "id": "branchType1",
        |  "displayName": "Branch Type One",
        |  "instanceNameFormat": "$content.string-field$",
        |  "fields": [
        |    {
        |      "id": "string-field",
        |      "type": "String",
        |      "displayName": "String field"
        |    }
        |  ],
        |  "cardinality": "Many"
        |}""".stripMargin

    val req = copybeansTypesUrl.POST
     .addQueryParameter("parent", "")
     .addQueryParameter("branch", "test")
     .setContentType("application/json", "UTF8")
     .setBody(json)

    doReq(req)
  }

  it should "get the test branch head" in {
    getBranchHead("test") should be("2R2W0Q382V94R")
  }

  it should "POST new branch copybeans" in {

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "branchType1"
        |  ],
        |  "content": {
        |    "string-field":"hello world"
        |  }
        |}""".stripMargin

    val req = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead("test"))
     .addQueryParameter("branch", "test")
     .setContentType("application/json", "UTF8")
     .setBody(json)

    doReq(req)
  }

  it should "get the branch out of the list of branches" in {
    val req = branchesUrl.GET
    doReqThen(req) { response =>
      assert(response.getResponseBody.contains("test"))
    }
  }

  it should "get the bean in the branch" in {
    val req = copybeansUrl.GET
     .addQueryParameter("branch", "test")

    doReqThen(req) { response =>
      val jsonArray = Json.parse(response.getResponseBody).as[JsArray]
      assert(jsonArray.value.size == 1)
      assert(jsonArray.value.head.\("content").\("string-field").as[JsString].value == "hello world")
    }
  }

  it should "get the branch commits" in {
    val req = branchCommitsUrl("test").GET

    doReqThen(req) { response =>
      val jsonArray = Json.parse(response.getResponseBody).as[JsArray]
      assert(jsonArray.value.size == 2)
      assert(jsonArray.value.head.\("parentCommitId").as[JsString].value.length > 0)
      assert(jsonArray.value(1).\("parentCommitId").as[JsString].value.length == 0)
      jsonArray.value.foreach { v =>
        assert(v.\("branchId").as[JsString].value == "test")
      }
    }
  }

}