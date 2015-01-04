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
package org.copygrinder.pure.copybean.search

import org.copygrinder.UnitTest
import org.copygrinder.pure.copybean.model.CopybeanImpl
import play.api.libs.json._

import scala.collection.immutable.ListMap

class DocumentBuilderTest extends UnitTest {

  val documentBuilder = new DocumentBuilder()

  "buildDocument" should "return a Document object that matches the supplied CopybeanImpl" in {
    val nestedObject = ("nested" -> ListMap(
      "nestedField" -> 123,
      "decField" -> 1.1
    ))
    val array = ("array" -> Seq(false, 3.14))
    val values = ListMap("stringField" -> "true", nestedObject, array)
    val doc = documentBuilder.buildDocument(CopybeanImpl("876", Set("someType"), values))

    doc.getField("id").stringValue() should be("876")
    doc.getField("enforcedTypeIds").stringValue() should be("someType")
    doc.getField("content.stringField").stringValue() should be("true")
    doc.getField("content.nested.nestedField").numericValue() should be(123)
    doc.getField("content.nested.decField").numericValue() should be(1.1)
    doc.getFields("content.array").map(_.stringValue()) should be(Array("false", "3.14"))
  }

}
