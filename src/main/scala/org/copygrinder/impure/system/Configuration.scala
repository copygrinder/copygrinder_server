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
package org.copygrinder.impure.system

import java.io.File

import com.typesafe.config.ConfigFactory

import scala.util.Try

class Configuration {

  protected val defaultPortNumber = 19836

  protected val defaultMaxResults = 100

  protected val config = ConfigFactory.parseFile(new File("copygrinder.conf"))

  lazy val serviceReadPort = Try(config.getInt("service.readPort")).getOrElse(defaultPortNumber)

  lazy val serviceWritePort = Try(config.getInt("service.writePort")).getOrElse(defaultPortNumber)

  lazy val serviceHost = Try(config.getString("service.host")).getOrElse("localhost")

  lazy val copybeanRepoRoot = Try(config.getString("copybeans.repoRoot")).getOrElse("data/copybeans")

  lazy val copybeanDefaultRepo = Try(config.getString("copybeans.defaultRepo")).getOrElse("default")

  lazy val indexRoot = Try(config.getString("index.root")).getOrElse("data/index")

  lazy val indexMaxResults = Try(config.getInt("index.maxResults")).getOrElse(defaultMaxResults)

}