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

import org.copygrinder.impure.copybean.persistence.{CopybeanPersistenceService, FilePersistenceService}
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.FieldType
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsObject, JsString, JsValue}
import spray.http.MultipartContent

import scala.concurrent.{ExecutionContext, Future, Await}
import scala.concurrent.duration._

class FileController(
 filePersistenceService: FilePersistenceService, copybeanPersistenceService: CopybeanPersistenceService
 ) extends JsonReads with JsonWrites with ControllerSupport {

  def getFile(id: String, field: String)(implicit siloScope: SiloScope, ec: ExecutionContext): (String, Array[Byte], String, String) = {
    val beanFuture = copybeanPersistenceService.cachedFetchCopybean(id)
    val bean = Await.result(beanFuture, 5 seconds)
    val value = bean.content.getOrElse(field,
      throw new JsonInputException("Field $field was not found in bean $id")
    )

    val typeFutures = bean.enforcedTypeIds.map(typeId => {
      copybeanPersistenceService.cachedFetchCopybeanType(typeId).map(beanType => {
        beanType.fields.flatMap(_.find(typeField => {
          typeField.id == field
        }))
      })
    })
    val typeField = Await.result(Future.sequence(typeFutures), 5 seconds).flatten.head

    val fileData = value.asInstanceOf[Map[String, String]]
    val hash = fileData.get("hash").get
    val filename = fileData.get("filename").get
    val (array, contentType) = filePersistenceService.getFile(hash)

    val disposition = if (typeField.`type` == FieldType.Image) {
      "inline"
    } else {
      "attachment"
    }

    (filename, array, contentType, disposition)
  }

  def storeFile(data: MultipartContent)(implicit siloScope: SiloScope): JsValue = {
    val hashes = data.parts.seq.map(part => {
      if (part.filename.isEmpty) {
        throw new JsonInputException("Filename is required.")
      }
      val contentType = part.headers.find(_.is("content-type")).getOrElse(
        throw new JsonInputException("content-type is required.")
      ).value
      val stream = part.entity.data.toChunkStream(128 * 1024)
      val filename = part.filename.get
      val hash = filePersistenceService.storeFile(filename, contentType, stream)
      (filename, hash)
    })
    JsObject(hashes.map { nameAndHash =>
      (nameAndHash._1, JsString(nameAndHash._2))
    })
  }

}
