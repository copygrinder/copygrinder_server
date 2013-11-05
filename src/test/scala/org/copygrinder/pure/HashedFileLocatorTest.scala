package org.copygrinder.pure

import org.scalatest.FlatSpec
import java.util.UUID
import java.io.File

class HashedFileLocatorTest extends FlatSpec {

  val locator = new HashedFileLocator()

  "locate" should "return a file with a directory 2 levels deep and with the right root and extension" in {
    assert(locator.locate("TEST", "json", new File("/rootdir")) === new File("/rootdir/T/E/TEST.json"))
  }

  "locate" should "fail for ids with less than 2 characters" in {
    intercept[RuntimeException] {
      locator.locate("A", "json", new File("/rootdir"))
    }
  }

}