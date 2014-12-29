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
import org.copygrinder.pure.copybean.exception.UnknownQueryParameter
import org.copygrinder.pure.copybean.model.{AnonymousCopybean, Copybean}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsNull, JsString, JsValue, Json}

class BeanController(persistenceService: CopybeanPersistenceService) extends JsonReads with JsonWrites with ControllerSupport {

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

  protected val copybeansReservedWords = Set("enforcedTypeIds", "id", "content", "type", "names")

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val (fields, nonFieldParams) = extractFields(params)

    nonFieldParams.foreach(param => {
      if (!copybeansReservedWords.exists(reservedWord => param._1.startsWith(reservedWord))) {
        throw new UnknownQueryParameter(param._1)
      }
    })

    val futures = persistenceService.find(nonFieldParams)
    validateAndFilterFields(fields, Json.toJson(futures), copybeansReservedWords)
  }

  def update(id: String, anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): JsValue = {
    persistenceService.update(id, anonCopybean)
    JsNull
  }

  def delete(id: String)(implicit siloScope: SiloScope): JsValue = {
    persistenceService.delete(id)
    JsNull
  }

  def createSilo()(implicit siloScope: SiloScope): JsValue = {
    persistenceService.createSilo()
    JsNull
  }

}
