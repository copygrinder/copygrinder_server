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

import play.api.libs.json.{JsArray, JsString, Json}


class ReferenceTest extends IntegrationTestSupport {

  def getId() = {
    val req = copybeansUrl.GET

    doReqThen(req) { response =>
      checkStatus(req, response)
      val json = Json.parse(response.getResponseBody).as[JsArray]
      val bean = json.value.find { bean =>
        val ids = bean.\("enforcedTypeIds").as[JsArray]
        ids.value.contains(JsString("reftype2"))
      }
      bean.get.\("id").as[JsString].value
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
        |          {"refValidationTypes": ["reftype2"], "refDisplayType": "reftype2"}
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
        |}]""".stripMargin

    val req = copybeansTypesUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    doReq(req)
  }

  it should "handle bad references" in {

    val json =
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
        |    "reftype1"
        |  ],
        |  "content": {
        |    "ref-field": {"ref":"1"}
        |  }
        |}]""".stripMargin

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    doReqThen(req, 400) { response =>
      assert(response.getResponseBody.contains("non-existent bean"))
    }

  }

  it should "handle good references" in {

    val id = getId()

    val json =
      """
        |{
        |  "enforcedTypeIds": [
        |    "reftype1"
        |  ],
        |  "content": {
        |    "ref-field": {"ref":"%s"},
        |    "reflist": [{"ref": "%s"}]
        |  }
        |}""".stripMargin.format(id, id)

    val req = copybeansUrl.POST.setContentType("application/json", "UTF8").setBody(json)

    doReq(req)
  }

  it should "handle expanding references" in {
    val req1 = copybeansUrl.GET.addQueryParameter("enforcedTypeIds", "reftype1")

    val req2 = req1.addQueryParameter("expand", "*")

    doReqThen(req1) { response =>
      assert(response.getResponseBody.contains("Awesome Value") == false)
    }

    doReqThen(req2) { response =>
      assert(response.getResponseBody.contains("Awesome Value"))
    }

  }


}