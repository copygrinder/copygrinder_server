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

import com.softwaremill.macwire.MacwireMacros._
import org.copygrinder.pure.copybean.model.Copybean
import org.copygrinder.unpure.system.Configuration
import org.json4s.jackson.Serialization._
import org.json4s.{DefaultFormats, Formats}

class PersistenceService {

  lazy val config = wire[Configuration]

  lazy val hashedFileNester = wire[HashedFileNester]

  lazy val gitRepo = new GitRepo(config.copybeanDefaultRepo)

  implicit def json4sJacksonFormats: Formats = DefaultFormats

  def store(copybean: Copybean): Unit = {

    gitRepo.createIfNonExistant()

    val pathAndFileName = hashedFileNester.nest(copybean.id, "json")

    gitRepo.add(pathAndFileName, write(copybean))
  }

}
