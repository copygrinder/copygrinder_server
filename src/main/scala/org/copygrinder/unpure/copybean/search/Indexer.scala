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
package org.copygrinder.unpure.copybean.search

import java.io.File

import com.softwaremill.macwire.MacwireMacros._
import org.apache.lucene.analysis.core.StopAnalyzer
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.{IndexWriter, IndexWriterConfig}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.copygrinder.unpure.system.Configuration

class Indexer {

  protected lazy val config = wire[Configuration]

  protected val analyzer = new StandardAnalyzer(Version.LUCENE_4_9, StopAnalyzer.ENGLISH_STOP_WORDS_SET)

  protected val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_9, analyzer)

  protected val indexWriter = new IndexWriter(FSDirectory.open(new File(config.indexRoot)), indexWriterConfig)

  def doCommit() = {
    indexWriter.commit()
  }

}
