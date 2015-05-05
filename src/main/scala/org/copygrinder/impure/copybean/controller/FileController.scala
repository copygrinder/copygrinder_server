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
import org.copygrinder.pure.copybean.exception.JsonInputException
import org.copygrinder.pure.copybean.model.ReifiedField.{FileOrImageReifiedField, ListReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.model.{CommitRequest, Namespaces, Trees}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{JsValue, Json}
import spray.http.MultipartContent

import scala.collection.immutable.ListMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class FileController(copybeanPersistenceService: CopybeanPersistenceService)
 extends JsonReads with JsonWrites with ControllerSupport {

  def getFile(id: String, field: String, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ec: ExecutionContext): (String, Array[Byte], String, String) = {

    val branchId = getBranchId(params)

    val headFuture = copybeanPersistenceService.getCommitIdOfActiveHeadOfBranch(Trees.userdata, branchId)

    val resultFuture = headFuture.flatMap(head => {

      val beanFuture = copybeanPersistenceService.fetchCopybeansFromCommit(Seq(id), head)

      beanFuture.flatMap(reifiedBeans => {

        val reifiedBean = reifiedBeans.head

        val reifiedField = getValue(field, reifiedBean).asInstanceOf[FileOrImageReifiedField]

        val metaDataId = reifiedField.metaData

        val metaDataFuture = siloScope.persistor.getByIdsAndCommit(
          Trees.userdata, Seq((Namespaces.bean, metaDataId)), head
        )

        metaDataFuture.flatMap { case (existingMetaDataOpt) =>

          val metaDataBean = existingMetaDataOpt.head.get.bean
          val hash = metaDataBean.content.get("hash").get.asInstanceOf[String]
          val filename = metaDataBean.content.get("filename").get.asInstanceOf[String]
          val contentType = metaDataBean.content.get("contentType").get.asInstanceOf[String]

          val disposition = if (reifiedField.fieldDef.`type` == FieldType.Image) {
            "inline"
          } else {
            "attachment"
          }

          val arrayFuture = siloScope.blobPersistor.getBlob(hash)

          arrayFuture.map(array => {
            (filename, array, contentType, disposition)
          })
        }
      })
    })

    Await.result(resultFuture, 5 seconds)
  }

  protected def getValue(field: String, bean: ReifiedCopybean): ReifiedField = {
    val result = parseField(field) { fieldId =>
      bean.fields.get(field)
    } { (fieldId, index) =>
      bean.fields.get(fieldId).map(field => {
        field.asInstanceOf[ListReifiedField].castVal(index)
      })
    }

    result.getOrElse(
      throw new JsonInputException("Field $field was not found in bean $id")
    )
  }

  def storeFile(data: MultipartContent, params: Map[String, List[String]])
   (implicit siloScope: SiloScope, ec: ExecutionContext): JsValue = {

    val branchId = getBranchId(params)
    val parentCommitId = getParentCommitId(params)

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
      val storeFuture = siloScope.blobPersistor.storeBlob(stream)
      storeFuture.flatMap { case (hash, length) =>
        createMetaData(filename, hash, length, contentType, branchId, parentCommitId)
      }
    })

    val resultFuture = Future.sequence(fileMetadataBeans)
    Json.toJson(resultFuture.map(_.map(_.id)))
  }

  protected def createMetaData(filename: String, hash: String, length: Long, contentType: String, branchId: String,
   parentCommitId: String)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[ReifiedCopybean] = {

    val metaData = new AnonymousCopybeanImpl(Set("fileMetadata"), ListMap(
      "filename" -> filename,
      "hash" -> hash,
      "sizeInBytes" -> length,
      "contentType" -> contentType
    ))

    val commit = new CommitRequest(Trees.userdata, branchId, parentCommitId, "", "")
    copybeanPersistenceService.storeAnonBean(Seq(metaData), commit).map(_._2.head)
  }

  def createSilo()(implicit siloScope: SiloScope, ec: ExecutionContext): Unit = {
    Await.result(siloScope.blobPersistor.initSilo(), 5 seconds)
  }

}
