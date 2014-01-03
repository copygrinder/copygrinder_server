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
package org.copygrinder.pure.copybean

import org.scalatest.FlatSpec
import org.copygrinder.pure.copybean.serialize.CopybeanSerializer

class CopybeanSerializerTest extends FlatSpec {

  val copybeanSerializer = new CopybeanSerializer()
  
  "copybeanSerializer" should "stuff" in {
    val nestedMap = Map("nested" -> 2)
    val cb = new Copybean("RAND0MID", Set("Blog"), Map("a" -> 1, "b" -> nestedMap))
    copybeanSerializer.serialize(cb)
  }

}