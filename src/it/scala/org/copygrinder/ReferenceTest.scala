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
import play.api.libs.json.{JsArray, JsString, Json}


class ReferenceTest extends IntegrationTestSupport {

  def getId(index: Int, typeId: String) = {
    val req = copybeansUrl.GET

    doReqThen(req) { response =>
      checkStatus(req, response)
      val json = Json.parse(response.getResponseBody).as[JsArray]
      val bean = json.value.filter { bean =>
        val ids = bean.\("enforcedTypeIds").as[JsArray]
        ids.value.contains(JsString(typeId))
      }
      bean(index).\("id").as[JsString].value
    }

  }

  "Copygrinder" should "POST new ref types" in {

    val json =
      """
        |[{
        |  "id": "reftype1",
        |  "displayName": "Reference Type One",
        |  "instanceNameFormat": "$content.ref-field$",
        |  "fields": [
        |    {
        |      "id": "ref-field",
        |      "type": "Reference",
        |      "displayName": "Reference field",
        |      "attributes": {
        |        "refs": [
        |          {"refValidationTypes": ["reftype2"], "refDisplayType": "reftype2"}
        |        ]
        |      }
        |    },{
        |      "id": "reflist",
        |      "type": "List",
        |      "displayName": "Reference List field",
        |      "attributes": {
        |        "listType": "Reference",
        |        "refs": [
        |          {"refValidationTypes": ["reftype2"], "refDisplayType": "reftype2"},
        |          {"refValidationTypes": ["reftype3"], "refDisplayType": "reftype3"}
        |        ]
        |      }
        |    }
        |  ],
        |  "cardinality": "One"
        |},{
        |  "id": "reftype2",
        |  "displayName": "Reference Type Two",
        |  "instanceNameFormat": "$content.stringfield$",
        |  "cardinality": "Many",
        |  "fields":
        |    [{
        |      "id": "stringfield",
        |      "type": "String",
        |      "displayName": "String Field"
        |    }]
        |},{
        |  "id": "reftype3",
        |  "displayName": "Reference Type Three",
        |  "instanceNameFormat": "$content.other-string-field$",
        |  "cardinality": "Many",
        |  "fields":
        |    [{
        |      "id": "other-string-field",
        |      "type": "String",
        |      "displayName": "String Field"
        |    }]
        |}]""".stripMargin

    val req = copybeansTypesUrl.POST
     .addQueryParameter("parent", getBranchHead())
     .setContentType("application/json", "UTF8")
     .setBody(json)

    doReq(req)
  }

  it should "handle good references" in {

    val json1 =
      """
        |[{
        |  "enforcedTypeIds": [
        |    "reftype2"
        |  ],
        |  "content": {
        |    "stringfield": "Awesome Value"
        |  }
        |},{
        |  "enforcedTypeIds": [
        |    "reftype3"
        |  ],
        |  "content": {
        |    "other-string-field": "Third Value"
        |  }
        |}]""".stripMargin

    val req1 = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead())
     .setContentType("application/json", "UTF8")
     .setBody(json1)

    doReq(req1)

    val id0 = getId(0, "reftype2")
    val id1 = getId(0, "reftype3")

    val json2 =
      """
        |{
        |  "enforcedTypeIds": [
        |    "reftype1"
        |  ],
        |  "content": {
        |    "ref-field": {"ref":"%s"},
        |    "reflist": [{"ref": "%s"}]
        |  }
        |}""".stripMargin.format(id0, id1)

    val req2 = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead())
     .setContentType("application/json", "UTF8")
     .setBody(json2)

    doReq(req2)
  }

  it should "handle bad references" in {

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "reftype1"
        |  ],
        |  "content": {
        |    "ref-field": {"ref":"1"}
        |  }
        |}""".stripMargin

    val req = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead())
     .setContentType("application/json", "UTF8")
     .setBody(json)

    doReqThen(req, 400) { response =>
      assert(response.getResponseBody.contains("non-existent bean"))
    }

    val id = getId(0, "reftype3")

    val json2 =
      """
        |{
        |  "enforcedTypeIds": [
        |    "reftype1"
        |  ],
        |  "content": {
        |    "ref-field": {"ref":"%s"}
        |  }
        |}""".stripMargin.format(id)

    val req2 = copybeansUrl.POST
     .addQueryParameter("parent", getBranchHead())
     .setContentType("application/json", "UTF8")
     .setBody(json2)

    doReqThen(req2, 400) { response =>
      assert(response.getResponseBody.contains("type not contained within refValidationTypes"))
    }

  }

  it should "handle expanding references" in {
    val req1 = copybeansUrl.GET.addQueryParameter("enforcedTypeIds", "reftype1")

    val req2 = req1.addQueryParameter("expand", "content.ref-field,content.reflist")

    doReqThen(req1) { response =>
      assert(response.getResponseBody.contains("Awesome Value") == false)
      assert(response.getResponseBody.contains("Third Value") == false)
    }

    doReqThen(req2) { response =>
      assert(response.getResponseBody.contains("Awesome Value"))
      assert(response.getResponseBody.contains("Third Value"))
    }
  }

  it should "handle field filtering with expanded references" in {
    val req = copybeansUrl.GET
     .addQueryParameter("enforcedTypeIds", "reftype1")
     .addQueryParameter("expand", "*")
     .addQueryParameter("fields", "content.ref-field")

    doReqThen(req) { response =>
      assert(response.getResponseBody.contains("Awesome Value"))
      assert(response.getResponseBody.contains("Third Value") == false)
    }
  }

}