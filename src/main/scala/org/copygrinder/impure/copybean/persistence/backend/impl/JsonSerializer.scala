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
import org.copygrinder.pure.copybean.persistence.model.{Namespaces, PersistableObject}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsString, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}

class JsonSerializer extends PersistentObjectSerializer[Array[Byte]] with JsonReads with JsonWrites {


  def serialize(persistableObject: PersistableObject): Array[Byte] = {
    if (persistableObject.beanOrType.isLeft) {
      unreifiedCopybeanWrites
       .writes(persistableObject.bean).toString()
       .getBytes(Charset.forName("UTF-8"))
    } else {
      implicitly[Writes[CopybeanType]]
       .writes(persistableObject.cbType).toString()
       .getBytes(Charset.forName("UTF-8"))
    }
  }

  def deserialize(namespace: String, fetchTypes: FetchTypes, data: Array[Byte])
   (implicit ec: ExecutionContext): Future[PersistableObject] = {
    val json = new String(data, "UTF-8")

    namespace match {
      case Namespaces.bean =>
        val bean = implicitly[Reads[CopybeanImpl]].reads(JsString(json)).get
        reifyBean(bean, fetchTypes).map(reifiedBean => {
          PersistableObject(reifiedBean)
        })
      case Namespaces.bean =>
        Future {
          val cbType = implicitly[Reads[CopybeanType]].reads(JsString(json)).get
          PersistableObject(cbType)
        }
      case other => throw new RuntimeException("Unknown Namespace " + other)
    }

  }

  protected def reifyBean(bean: Copybean, fetchTypes: FetchTypes)
   (implicit ec: ExecutionContext): Future[ReifiedCopybean] = {
    fetchTypes(bean.enforcedTypeIds).map(types => ReifiedCopybean(bean, types))
  }

}