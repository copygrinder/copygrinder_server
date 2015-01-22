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
package org.copygrinder.impure.copybean.persistence

import java.io.File
import java.security.MessageDigest

import encoding.CrockfordBase32
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.model.FileMetadata
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import play.api.libs.json.{Json, Reads, Writes}
import spray.http.HttpData

class FilePersistenceService(
 hashedFileResolver: HashedFileResolver
 ) extends JsonReads with JsonWrites {

  def getFile(hash: String)(implicit siloScope: SiloScope): (Array[Byte], String) = {

    val destMetaFile = hashedFileResolver.locate(hash, "json", siloScope.fileDir)
    val json = FileUtils.readFileToByteArray(destMetaFile)
    val metaData = implicitly[Reads[FileMetadata]].reads(Json.parse(json)).get

    val destBlobFile = hashedFileResolver.locate(hash, "blob", siloScope.fileDir)
    val array = FileUtils.readFileToByteArray(destBlobFile)
    (array, metaData.contentType)
  }

  def storeFile(filename: String, contentType: String, stream: Stream[HttpData])(implicit siloScope: SiloScope): String = {
    FileUtils.forceMkdir(siloScope.tempDir)
    val tempFile = File.createTempFile("blob", ".tmp", siloScope.tempDir)
    tempFile.deleteOnExit()
    val digest = MessageDigest.getInstance("SHA-256")
    stream.foreach(data => {
      val byteArray = data.toByteString.toArray
      FileUtils.writeByteArrayToFile(tempFile, byteArray, true)
      digest.update(byteArray)
    })

    val hash = new CrockfordBase32().encodeToString(digest.digest())
    val destBlobFile = hashedFileResolver.locate(hash, "blob", siloScope.fileDir)
    if (!destBlobFile.exists()) {
      FileUtils.forceMkdir(destBlobFile.getParentFile)
      FileUtils.moveFile(tempFile, destBlobFile)
    }


    val destMetaFile = hashedFileResolver.locate(hash, "json", siloScope.fileDir)
    val metaData = if (destMetaFile.exists()) {
      val json = FileUtils.readFileToByteArray(destMetaFile)
      implicitly[Reads[FileMetadata]].reads(Json.parse(json)).get
    } else {
      new FileMetadata(Set(), destBlobFile.length(), contentType)
    }

    val newMetaData = metaData.copy(filenames = metaData.filenames + filename)

    if (metaData != newMetaData) {
      val newJson = Json.stringify(implicitly[Writes[FileMetadata]].writes(newMetaData))
      FileUtils.writeStringToFile(destMetaFile, newJson, "UTF-8")
    }
    hash
  }

}
