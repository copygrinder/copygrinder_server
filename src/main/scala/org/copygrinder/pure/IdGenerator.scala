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

  protected val utf8 = Charset.forName("UTF-8");

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
    
    val buffer = ByteBuffer.allocate(8)
    buffer.put(bytes.take(8))
    buffer.flip()
    val most = buffer.getLong()
    
    val buffer2 = ByteBuffer.allocate(8)
    buffer2.put(bytes.takeRight(8))
    buffer2.flip()
    val least = buffer2.getLong()
    
    new UUID(most, least)
  }

}