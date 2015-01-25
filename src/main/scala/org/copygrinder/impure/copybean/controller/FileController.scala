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

import monocle.Lens
import monocle.macros.Lenser
import org.copygrinder.impure.copybean.persistence.{CopybeanPersistenceService, FilePersistenceService}
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import spray.http.MultipartContent

import scala.collection.immutable.ListMap
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
      val (hash, length) = filePersistenceService.storeFile(filename, contentType, stream)

      handleMetaData(filename, hash, length)

      (filename, hash)
    })
    val out = hashes.map { nameAndHash =>
      JsObject(Seq(("filename", JsString(nameAndHash._1)), ("hash", JsString(nameAndHash._2))))
    }
    JsArray(out)
  }

  protected def handleMetaData(filename: String, hash: String, length: Long)(implicit siloScope: SiloScope) {
    val metaDataFuture = copybeanPersistenceService.find(Seq(("enforcedTypeIds", "ImageMetadata"), ("hash", hash)))
    val existingMetaData = Await.result(metaDataFuture, 5 seconds).headOption

    if (existingMetaData.isDefined) {
      val metaData = existingMetaData.get
      val filenames = metaData.content.get("filenames").asInstanceOf[Seq[String]]
      if (!filenames.contains(filename)) {
        val newMetaData = Lenser[ReifiedCopybeanImpl](_.content).modify(oldContent => {
          oldContent.updated("filenames", filenames + filename)
        })(metaData.asInstanceOf[ReifiedCopybeanImpl])
        copybeanPersistenceService.update(newMetaData.id, newMetaData)
      }
    } else {
      val metaData = new AnonymousCopybeanImpl(Set("ImageMetadata"), ListMap(
        "filenames" -> Seq(filename),
        "hash" -> hash,
        "length" -> length
      ))
      copybeanPersistenceService.store(metaData)
    }
  }
}
