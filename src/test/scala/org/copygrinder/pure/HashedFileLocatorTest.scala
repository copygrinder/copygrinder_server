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

import org.scalatest.FlatSpec
import java.util.UUID
import java.io.File

class HashedFileLocatorTest extends FlatSpec {

  val locator = new HashedFileLocator()

  "locate" should "return a file with a directory 2 levels deep and with the right root and extension" in {
    assert(locator.locate("TEST", "json", new File("/rootdir")) === new File("/rootdir/T/E/TEST.json"))
  }

  "locate" should "fail for ids with less than 2 characters" in {
    intercept[RuntimeException] {
      locator.locate("A", "json", new File("/rootdir"))
    }
  }

}