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

import org.copygrinder.impure.copybean.persistence.CopybeanPersistenceService
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}

class BeanController(persistenceService: CopybeanPersistenceService) extends JsonReads with JsonWrites {

  def cachedFetchCopybean(id: String)(implicit siloScope: SiloScope): JsValue = {
    val future = persistenceService.cachedFetchCopybean(id)
    Json.toJson(future)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): JsString = {
    val id = persistenceService.store(anonCopybean)
    JsString(id)
  }

  def store(copybean: Copybean)(implicit siloScope: SiloScope): JsString = {
    val id = persistenceService.store(copybean)
    JsString(id)
  }

  def find()(implicit siloScope: SiloScope): JsValue = {
    val futures = persistenceService.find()
    Json.toJson(futures)
  }


  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val futures = persistenceService.find(params)
    Json.toJson(futures)
  }

  def update(id: String, anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): JsValue = {
    persistenceService.update(id, anonCopybean)
    JsNull
  }

}
