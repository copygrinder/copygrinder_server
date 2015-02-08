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

import com.ning.http.client.Response
import dispatch.Req

trait TestSupport {

  def checkStatus(req: Req, response: Response, code: Int = 200) = {
    val status = response.getStatusCode
    if (status != code) {
      println("REQUEST: " + req.toRequest)
      println("REQUEST BODY: " + req.toRequest.getStringData)
      println("RESPONSE: " + response.getResponseBody)
      assert(status == code)
    }
  }

}
