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

import org.copygrinder.impure.copybean.persistence.TypePersistenceService
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.CopybeanType
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json._

import scala.collection.Seq
import scala.concurrent.ExecutionContext

class TypeController(persistenceService: TypePersistenceService) extends JsonReads with JsonWrites with ControllerSupport {

  def fetchAllCopybeanTypes()(implicit siloScope: SiloScope): JsValue = {
    val futures = persistenceService.fetchAllCopybeanTypes()
    Json.toJson(futures)
  }

  def findCopybeanTypes(params: Seq[(String, String)])(implicit siloScope: SiloScope): JsValue = {
    val (fields, nonFieldParams) = extractFields(params)
    val futures = persistenceService.findCopybeanTypes(nonFieldParams)
    validateAndFilterFields(fields, Json.toJson(futures), copybeanTypeReservedWords)
  }


  def update(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {
    persistenceService.update(copybeanType)
  }

  def store(copybeanType: CopybeanType)(implicit siloScope: SiloScope): Unit = {
    persistenceService.store(copybeanType)
  }

  def fetchCopybeanType(id: String)(implicit siloScope: SiloScope, ex: ExecutionContext): JsValue = {
    val future = persistenceService.cachedFetchCopybeanType(id)
    Json.toJson(future)
  }

  protected val copybeanTypeReservedWords = Set("id", "displayName", "instanceNameFormat", "instanceNameFormat", "fields", "validators", "cardinality")

}
