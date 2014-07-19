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
package org.copygrinder.unpure.system

import com.typesafe.config.ConfigFactory

import scala.util.Try

class Configuration {

  protected val defaultPortNumber = 8080

  protected val config = ConfigFactory.load()

  lazy val servicePort = Try(config.getInt("service.port")).getOrElse(defaultPortNumber)

  lazy val serviceHost = Try(config.getString("service.host")).getOrElse("localhost")
}