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
package org.copygrinder.unpure.copybean.persistence

class HashedFileNester {

  def nest(id: String, extension: String): String = {

    if (id.length() < 2) {
      throw new RuntimeException(s"The id '$id' must be at least 2 characters long.")
    }

    val subDirectory1 = id.charAt(0)
    val subDirectory2 = id.charAt(1)
    val extensionWithDot = if (extension.nonEmpty) s".$extension" else ""
    s"$subDirectory1/$subDirectory2/$id$extensionWithDot"
  }

}