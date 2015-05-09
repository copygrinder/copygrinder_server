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
package org.copygrinder.pure.collections

import org.copygrinder.UnitTest

class IndexedHashMapTest extends UnitTest {

  val testMap = new IndexedHashMap[String, Int]()

  "ImmutableLinkedHashMap" should "have the right order and value" in {

    val map1 = testMap ++ Seq("hello" -> 1, "other" -> 2)
    val map3 = map1 + ("stuff" -> 3)
    val map4 = map3 + ("And things" -> 0)

    val results = map4.toSeq

    results.head._1 should be("hello")
    results(1)._1 should be("other")
    results(2)._1 should be("stuff")

    map4("stuff") should be(3)
  }

  it should "handle construction adds" in {

    val map = IndexedHashMap[String, Int]("hello" -> 1, "other" -> 2)

    map.seq.head._2 should be(1)
  }

  it should "handle collisions" in {

    val map1 = testMap + ("hello" -> 1)
    val map2 = map1 + ("hello" -> 3)

    map2("hello") should be(3)
    map2.size should be(1)
  }

  it should "delete entries" in {

    val map1 = testMap + ("hello" -> 1)
    val map2 = map1 + ("world" -> 2)
    val map3 = map2 - "hello"

    map3.size should be(1)
    map2("world") should be(2)
  }

  it should "handle updates preserving order" in {

    val map1 = testMap + ("hello" -> 1)
    val map2 = map1 + ("world" -> 2)
    val map3 = map2.updated("hello", 0)

    map3("hello") should be(0)
    map3("world") should be(2)
    map3.head._2 should be(0)
  }

}