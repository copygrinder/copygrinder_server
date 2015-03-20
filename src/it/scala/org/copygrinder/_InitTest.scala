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

import scala.concurrent.Await
import scala.concurrent.duration._


class _InitTest extends IntegrationTestSupport {


  "Copygrinder" should "give a basic response to root GETs" in {

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)

    FileUtils.deleteDirectory(siloDir)

    val req = rootUrl.GET

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 2 second)
  }

  "Copygrinder" should "create and initalize a silo" in {

    val req = baseUrl.POST

    val responseFuture = Http(req).map { response =>
      checkStatus(req, response)
    }

    Await.result(responseFuture, 2 second)

    val siloDir = new File(wiring.globalModule.configuration.copybeanDataRoot, siloId)
    assert(siloDir.exists)
  }

}