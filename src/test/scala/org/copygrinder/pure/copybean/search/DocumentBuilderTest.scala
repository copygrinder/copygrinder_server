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
import org.copygrinder.pure.copybean.model.Copybean
import org.json4s.JsonAST.{JField, JObject, JString}

class DocumentBuilderTest extends UnitTest {

  val documentBuilder = new DocumentBuilder

  "buildDocument" should "return a Document object that matches the supplied Copybean" in {
    val doc = documentBuilder.buildDocument(Copybean("876", Set("someType"), JObject(JField("panda", JString("true")))))
    doc.getField("id").stringValue() should be("876")
    doc.getField("enforcedType").stringValue() should be("someType")
    doc.getField("values.panda").stringValue() should be("true")
  }

}
