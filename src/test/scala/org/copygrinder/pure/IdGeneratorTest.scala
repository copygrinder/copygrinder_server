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
package org.copygrinder.pure

import java.io.File
import java.util.UUID

import org.scalatest.FlatSpec

class IdGeneratorTest extends FlatSpec {
  
  val idGenerator = new IdGenerator();

  val uuid1 = new UUID(0, 0)
  val uuid2 = new UUID(5234423498494123456L, 2234423498494123452L)
  
  "encodeUuid" should "return a string of 25 zeros for a zero UUID" in {
    assert(idGenerator.encodeUuid(uuid1) === "00000000000000000000000000")
  }
  
  "decodeUuid" should "be able to rebuild a UUID" in {
    assert(idGenerator.decodeUuid(idGenerator.encodeUuid(uuid2)) === uuid2)
  }
  
}