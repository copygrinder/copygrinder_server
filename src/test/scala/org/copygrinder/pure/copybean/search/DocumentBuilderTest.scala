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
import org.copygrinder.pure.copybean.model.{AnonymousCopybeanImpl, CopybeanImpl}
import org.json4s.JsonAST._

class DocumentBuilderTest extends UnitTest {

  val documentBuilder = new DocumentBuilder()

  "buildDocument" should "return a Document object that matches the supplied CopybeanImpl" in {
    val nestedObject = JField("nested", JObject(
      JField("nestedField", JInt(123)),
      JField("decField", JDecimal(1.1)),
      JField("nullField", JNull)
    ))
    val array = JField("array", JArray(List(JBool(false), JDouble(3.14))))
    val values = JObject(JField("stringField", JString("true")), nestedObject, array)
    val anonBean = new AnonymousCopybeanImpl(Set("someType"), values)
    val doc = documentBuilder.buildDocument(CopybeanImpl(anonBean, "876"))

    doc.getField("id").stringValue() should be("876")
    doc.getField("enforcedTypeIds").stringValue() should be("someType")
    doc.getField("contains.stringField").stringValue() should be("true")
    doc.getField("contains.nested.nestedField").numericValue() should be(123)
    doc.getField("contains.nested.decField").numericValue() should be(1.1)
    doc.getField("contains.nested.nullField").stringValue() should be("null")
    doc.getFields("contains.array").map(_.stringValue()) should be(Array("false", "3.14"))
  }

}
