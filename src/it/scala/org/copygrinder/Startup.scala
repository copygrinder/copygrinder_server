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

import org.copygrinder.impure.system.Boot
import org.scalatest.{FlatSpec, Matchers}
import dispatch._, Defaults._


class Startup extends FlatSpec with Matchers {

  "Boot" should "start" in {
    Boot.main(Array())

    //println(Boot.system)

    Thread.sleep(3000)

    val svc = url("http://localhost:8080")
    val data = Http(svc OK as.String)
    println(data())

    val svc2 = url("http://localhost:8080/copybeans/123")
    val data2 = Http(svc2 > as.String)
    println(data2())

    def myPostWithParams = url("http://localhost:8080/copybeans").POST.setContentType("application/json", "UTF8").setBody("""{"values":{"name":"joe","age":16}}""")
    val data3 = Http(myPostWithParams > as.String)
    println(data3())

    def myPostWithBadParams = url("http://localhost:8080/copybeans").POST.setBody("""{"values":{"name":"joe","age":16}}""")
    val data4 = Http(myPostWithBadParams > as.String)
    println(data4())
  }

}