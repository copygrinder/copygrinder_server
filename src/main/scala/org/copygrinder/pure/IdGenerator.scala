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
package org.copygrinder.pure

import java.util.UUID
import encoding.CrockfordBase32
import java.util.Arrays
import java.nio.charset.Charset
import java.io.ObjectOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

class IdGenerator {

  protected val utf8 = Charset.forName("UTF-8")

  protected val byteSize = 8

  def generateEncodedUuid(): String = {
    encodeUuid(UUID.randomUUID())
  }

  def encodeUuid(uuid: UUID): String = {

    val bos = new ByteArrayOutputStream
    val out = new DataOutputStream(bos)
    out.writeLong(uuid.getMostSignificantBits())
    out.writeLong(uuid.getLeastSignificantBits())
    out.close()

    val mappedByteArray = bos.toByteArray

    val encoded = new CrockfordBase32().encodeToString(mappedByteArray.toArray)
    encoded
  }

  def decodeUuid(encoded: String): UUID = {
    val bytes = new CrockfordBase32().decode(encoded)

    val buffer = ByteBuffer.allocate(byteSize)
    buffer.put(bytes.take(byteSize))
    buffer.flip()
    val most = buffer.getLong()

    val buffer2 = ByteBuffer.allocate(byteSize)
    buffer2.put(bytes.takeRight(byteSize))
    buffer2.flip()
    val least = buffer2.getLong()

    new UUID(most, least)
  }

}