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
package org.copygrinder.unpure

import org.copygrinder.pure.IdGenerator
import org.copygrinder.pure.copybean.Copybean
import org.copygrinder.pure.copybean.serialize.CopybeanSerializer
import java.io.File

object CopybeanService {

  val idGenerator = new IdGenerator

  val haashedFileLocator = new HashedFileLocator

  val git = new GitRepo("copybeans")

  val serializer = new CopybeanSerializer

  git.createIfNonExistant()

  def createAndPersist(enforcedTypeIds: Set[String], values: Map[String, Any]) {
    val copybean = new Copybean(idGenerator.generateEncodedUuid, enforcedTypeIds, values)
    val file = haashedFileLocator.locate(copybean.id, "json", new File(""))
    val json = serializer.serialize(copybean)
    git.add(file.getName(), json)
    git.commit("")
  }

}