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
package org.copygrinder.impure.copybean.search

import java.io.File

import org.apache.commons.io.FileUtils
import org.copygrinder.pure.copybean.model.{CopybeanImpl, CopybeanType}
import org.copygrinder.pure.copybean.persistence.{JsonReads, PredefinedCopybeanTypes, PredefinedCopybeans}
import play.api.libs.json.{Json, Reads}

import scala.collection.JavaConversions._

class IndexRebuilder(
 indexer: Indexer,
 predefBeans: PredefinedCopybeans,
 predefTypes: PredefinedCopybeanTypes,
 beanDir: File,
 typesDir: File,
 indexDir: File
 ) extends JsonReads {

  rebuildIfNeeded()

  def rebuildIfNeeded(): Unit = {
    if (indexDir.exists() && indexer.upgrade()) {
      indexer.init()
    } else {
      rebuild()
    }
  }

  def rebuild(): Unit = {

    FileUtils.deleteDirectory(indexDir)

    indexer.init()

    addPredefs()
    addTypes()
    addBeans()
  }

  protected def addPredefs(): Unit = {
    val beans = predefBeans.predefinedBeans.map(_._2)
    beans.foreach { bean =>
      indexer.addCopybean(bean)
    }

    val types = predefTypes.predefinedTypes.map(_._2)
    types.foreach { beanType =>
      indexer.addCopybeanType(beanType)
    }
  }

  protected def addTypes(): Unit = {
    if (typesDir.exists()) {
      val typeFiles = FileUtils.listFiles(typesDir, Array("json"), true)
      typeFiles.foreach(typeFile => {
        val json = FileUtils.readFileToByteArray(typeFile)
        val cbType = implicitly[Reads[CopybeanType]].reads(Json.parse(json)).get
        indexer.addCopybeanType(cbType)
      })
    }
  }

  protected def addBeans(): Unit = {
    if (beanDir.exists()) {
      val beanFiles = FileUtils.listFiles(beanDir, Array("json"), true)
      beanFiles.foreach(beanFile => {
        val json = FileUtils.readFileToByteArray(beanFile)
        val bean = implicitly[Reads[CopybeanImpl]].reads(Json.parse(json)).get
        indexer.addCopybean(bean)
      })
    }
  }

}