package org.copygrinder.pure

import java.io.File
import java.util.UUID

import org.scalatest.FlatSpec

class IdGeneratorTest extends FlatSpec {
  
  val idGenerator = new IdGenerator();

  val uuid1 = new UUID(0, 0)
  val uuid2 = new UUID(5234423498494123456L, 2234423498494123452L)
  
  "encodeUuid" should "return a string of 25 zeros for a zero UUID" in {
    assert(idGenerator.encodeUuid(uuid1) === "00000000000000000000000000")
  }
  
  "decodeUuid" should "be able to rebuild a UUID" in {
    assert(idGenerator.decodeUuid(idGenerator.encodeUuid(uuid2)) === uuid2)
  }
  
}