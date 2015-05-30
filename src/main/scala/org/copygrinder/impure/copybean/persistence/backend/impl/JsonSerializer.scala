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
package org.copygrinder.impure.copybean.persistence.backend.impl

import java.nio.charset.Charset

import org.copygrinder.impure.copybean.persistence.backend.PersistentObjectSerializer
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{Json, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}

class JsonSerializer extends PersistentObjectSerializer[Array[Byte]] with JsonReads with JsonWrites {


  def serialize(persistableObject: ReifiedCopybean): Array[Byte] = {
    val json = unreifiedCopybeanWrites.writes(persistableObject)
    json.toString().getBytes(Charset.forName("UTF-8"))
  }

  def deserialize(fetchTypes: FetchTypes, data: Array[Byte])(implicit ec: ExecutionContext): Future[ReifiedCopybean] = {
    val beanResult = deserializeCopybean(data)
    reifyBean(beanResult, fetchTypes)
  }

  def reifyBean(bean: Copybean, fetchTypes: FetchTypes)(implicit ec: ExecutionContext): Future[ReifiedCopybean] = {
    fetchTypes(bean.enforcedTypeIds).map(types => ReifiedCopybean(bean, types))
  }

  override def deserializeCopybean(data: Array[Byte])(implicit ec: ExecutionContext): Copybean = {
    val json = new String(data, "UTF-8")

    val jsonParsed = Json.parse(json)
    implicitly[Reads[CopybeanImpl]].reads(jsonParsed).get
  }
}