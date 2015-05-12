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

  "Copygrinder" should "POST new branch types" in {

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

    val req = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead())
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

  it should "get the test branch head" in {
    getBranchHead("test") should be("2R2W0Q382V94R")
  }

  it should "get the branch out of the list of branches" in {
    val req = branchesUrl.GET
    doReqThen(req) { response =>
      assert(response.getResponseBody.contains("test"))
    }
  }

}