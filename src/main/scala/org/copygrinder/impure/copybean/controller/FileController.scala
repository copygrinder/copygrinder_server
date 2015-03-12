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

import monocle.macros.Lenser
import org.copygrinder.impure.copybean.persistence.{CopybeanPersistenceService, FilePersistenceService}
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.ReifiedField.{FileOrImageReifiedField, ListReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsValue, Json}
import spray.http.MultipartContent

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FileController(
 filePersistenceService: FilePersistenceService, copybeanPersistenceService: CopybeanPersistenceService
 ) extends JsonReads with JsonWrites with ControllerSupport {

  def getFile(id: String, field: String)
   (implicit siloScope: SiloScope, ec: ExecutionContext): (String, Array[Byte], String, String) = {
    val beanFuture = copybeanPersistenceService.cachedFetchCopybean(id)
    val bean = Await.result(beanFuture, 5 seconds)

    val reifiedBean = bean.asInstanceOf[ReifiedCopybeanImpl]

    val reifiedField = getValue(field, reifiedBean).asInstanceOf[FileOrImageReifiedField]

    val hash = reifiedField.hash
    val filename = reifiedField.filename
    val array = filePersistenceService.getFile(hash)

    val metaDataFuture = copybeanPersistenceService.find(
      Seq(("enforcedTypeIds", "fileMetadata"), ("content.hash", hash))
    )
    val existingMetaData = Await.result(metaDataFuture, 5 seconds).headOption.getOrElse(
      throw new JsonInputException(s"Metadata for hash not found: $hash")
    )
    val contentType = existingMetaData.content.get("contentType").get.asInstanceOf[String]

    val disposition = if (reifiedField.fieldDef.`type` == FieldType.Image) {
      "inline"
    } else {
      "attachment"
    }

    (filename, array, contentType, disposition)
  }

  protected def getValue(field: String, bean: ReifiedCopybeanImpl): ReifiedField = {
    val result = if (field.endsWith(")")) {
      val fieldId = field.takeWhile(_ != '(')
      val index = field.dropWhile(_ != '(').drop(1).takeWhile(_ != ')').toInt
      bean.fields.get(fieldId).map(field => {
        field.asInstanceOf[ListReifiedField].castVal(index)
      })
    } else {
      bean.fields.get(field)
    }

    result.getOrElse(
      throw new JsonInputException("Field $field was not found in bean $id")
    )
  }

  def storeFile(data: MultipartContent)(implicit siloScope: SiloScope): JsValue = {
    val fileMetadataBeans = data.parts.seq.map(part => {
      if (part.filename.isEmpty) {
        throw new JsonInputException("Filename is required.")
      }
      if (part.entity.isEmpty) {
        throw new JsonInputException("Payload can't be empty.")
      }
      val contentType = part.entity.toOption.get.contentType.value
      val stream = part.entity.data.toChunkStream(128 * 1024)
      val filename = part.filename.get
      val (hash, length) = filePersistenceService.storeFile(filename, contentType, stream)

      handleMetaData(filename, hash, length, contentType)
    })

    Json.toJson(fileMetadataBeans)
  }

  protected def handleMetaData(filename: String, hash: String, length: Long, contentType: String)
   (implicit siloScope: SiloScope): Copybean = {
    val metaDataFuture = copybeanPersistenceService.find(
      Seq(("enforcedTypeIds", "fileMetadata"), ("content.hash", hash))
    )
    val existingMetaData = Await.result(metaDataFuture, 5 seconds).headOption

    val metaData = if (existingMetaData.isDefined) {
      val metaData = existingMetaData.get
      val filenames = metaData.content.get("filenames").get.asInstanceOf[Seq[String]]
      if (!filenames.contains(filename)) {
        val newMetaData = Lenser[ReifiedCopybeanImpl](_.content).modify(oldContent => {
          oldContent.updated("filenames", filenames + filename)
        })(metaData.asInstanceOf[ReifiedCopybeanImpl])
        copybeanPersistenceService.update(newMetaData.id, newMetaData)
        newMetaData
      } else {
        metaData
      }
    } else {
      val metaData = new AnonymousCopybeanImpl(Set("fileMetadata"), ListMap(
        "filenames" -> Seq(filename),
        "hash" -> hash,
        "sizeInBytes" -> length,
        "contentType" -> contentType
      ))
      copybeanPersistenceService.store(metaData)
    }

    metaData
  }
}
