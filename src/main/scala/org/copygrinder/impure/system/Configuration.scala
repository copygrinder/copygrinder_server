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

import ch.qos.logback.classic.{Level, LoggerContext}
import com.typesafe.config.{ConfigRenderOptions, ConfigValueFactory, ConfigFactory}
import org.apache.commons.io.FileUtils
import org.slf4j.LoggerFactory

import scala.util.Try

class Configuration {

  protected val defaultPortNumber = 19836

  protected val defaultMaxResults = 100

  protected var config = ConfigFactory.parseFile(new File("copygrinder.conf"))

  lazy val serviceReadPort = Try(config.getInt("service.readPort")).getOrElse(defaultPortNumber)

  lazy val serviceWritePort = Try(config.getInt("service.writePort")).getOrElse(defaultPortNumber)

  lazy val serviceHost = Try(config.getString("service.host")).getOrElse("localhost")

  lazy val serviceThreads = Try(config.getInt("service.threads")).getOrElse(Runtime.getRuntime.availableProcessors())

  lazy val copybeanDataRoot = Try(config.getString("copybeans.dataRoot")).getOrElse("data")

  lazy val indexMaxResults = Try(config.getInt("index.maxResults")).getOrElse(defaultMaxResults)

  val loggingLevel = Try(config.getString("logging.level")).getOrElse("WARN")

  val loggingContext = LoggerFactory.getILoggerFactory().asInstanceOf[LoggerContext]

  loggingContext.getLogger("org.copygrinder").setLevel(Level.toLevel(loggingLevel))

  lazy val keyStoreLocation = Try(config.getString("keystore.file")).getOrElse("")

  lazy val keyStorePassword = Try(config.getString("keystore.password")).getOrElse("")

  def passwordHash: String = Try(config.getString("user.admin.hash")).getOrElse("")

  def updatePasswordHash(hash: String): Unit = {
    config = config.withValue("user.admin.hash", ConfigValueFactory.fromAnyRef(hash))
    val options = ConfigRenderOptions.defaults().setOriginComments(false)
    val newConfig = config.root().render(options)
    FileUtils.writeStringToFile(new File("copygrinder.conf"), newConfig)
  }

}