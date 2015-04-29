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

import java.io.File
import java.security.MessageDigest

import encoding.CrockfordBase32
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.persistence.HashedFileResolver
import org.copygrinder.impure.copybean.persistence.backend.BlobPersistor
import org.copygrinder.pure.copybean.exception.{SiloAlreadyInitialized, SiloNotInitialized}
import org.copygrinder.pure.copybean.persistence.{JsonReads, JsonWrites}
import spray.http.HttpData

import scala.concurrent.{ExecutionContext, Future, blocking}

class FileBlobPersistor(hashedFileResolver: HashedFileResolver, fileDir: File, tempDir: File, siloId: String)
 extends BlobPersistor with JsonReads with JsonWrites {

  def getBlob(hash: String)(implicit ec: ExecutionContext): Future[Array[Byte]] = {
    Future {
      blocking {

        if (!fileDir.exists()) {
          throw new SiloNotInitialized(siloId)
        }

        val destBlobFile = hashedFileResolver.locate(hash, "blob", fileDir)
        FileUtils.readFileToByteArray(destBlobFile)
      }
    }
  }

  def storeBlob(stream: Iterable[HttpData])(implicit ec: ExecutionContext): Future[(String, Long)] = {
    Future {
      blocking {

        if (!tempDir.exists() || !fileDir.exists()) {
          throw new SiloNotInitialized(siloId)
        }

        val tempFile = File.createTempFile("blob", ".tmp", tempDir)
        tempFile.deleteOnExit()
        val digest = MessageDigest.getInstance("SHA-256")
        stream.foreach(data => {
          val byteArray = data.toByteString.toArray
          FileUtils.writeByteArrayToFile(tempFile, byteArray, true)
          digest.update(byteArray)
        })

        val hash = new CrockfordBase32().encodeToString(digest.digest())
        val destBlobFile = hashedFileResolver.locate(hash, "blob", fileDir)
        if (!destBlobFile.exists()) {
          FileUtils.forceMkdir(destBlobFile.getParentFile)
          FileUtils.moveFile(tempFile, destBlobFile)
        }

        (hash, destBlobFile.length())
      }
    }
  }

  def initSilo()(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      blocking {
        if (tempDir.exists()) {
          throw new SiloAlreadyInitialized(siloId)
        }
        FileUtils.forceMkdir(tempDir)
      }
    }
  }
}
