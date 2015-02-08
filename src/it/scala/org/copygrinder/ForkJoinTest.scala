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

import dispatch.Defaults._
import dispatch._
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._


class ForkJoinTest extends FlatSpec with Matchers with TestSupport {

  TestWiring

  val testDuration = 1000

  val allowedOverhead = 200

  "Spray" should "Fork Join properly" in {

    val shortReq = url("http://localhost:9999/shortpause")

    val longReq = url("http://localhost:9999/longpause")

    val responseFuture = Http(shortReq).map { response =>
      checkStatus(shortReq, response)
    }

    Await.result(responseFuture, 1 second)

    val time1 = System.nanoTime()

    val longFutures = 1.to(2).map { _ =>
      Http(longReq).map { response =>
        checkStatus(longReq, response)
      }
    }

    val shortFutures = 1.to(10).map { _ =>
      Http(shortReq).map { response =>
        checkStatus(shortReq, response)
      }
    }

    val futureSeq = Future.sequence(longFutures ++ shortFutures)
    Await.result(futureSeq, 10 second)

    val time2 = System.nanoTime()
    val duration = (time2 - time1) / 1000 / 1000

    assert(duration < (testDuration + allowedOverhead))

  }

}