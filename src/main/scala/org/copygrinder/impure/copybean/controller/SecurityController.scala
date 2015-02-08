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
package org.copygrinder.impure.copybean.controller

import com.lambdaworks.crypto.SCryptUtil
import org.copygrinder.impure.system.Configuration
import spray.routing.authentication.UserPass

class SecurityController(config: Configuration) {

  def auth(userPass: Option[UserPass]): Boolean = {
    if (config.passwordHash.isEmpty) {
      true
    } else {
      userPass.exists(userPass => {
        SCryptUtil.check(userPass.pass, config.passwordHash)
      })
    }
  }

  val CPU_COST = 16384

  val MEMORY_COST = 8

  val PARALLEL_COST = 1

  def updatePassword(pass: String): Unit = {
    val hash = SCryptUtil.scrypt(pass, CPU_COST, MEMORY_COST, PARALLEL_COST)
    config.updatePasswordHash(hash)
  }

}