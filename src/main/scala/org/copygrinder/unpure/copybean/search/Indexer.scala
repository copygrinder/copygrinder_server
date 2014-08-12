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
import org.apache.lucene.analysis.core.{KeywordAnalyzer, StopAnalyzer}
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig, Term}
import org.apache.lucene.search.{MatchAllDocsQuery, IndexSearcher, PhraseQuery}
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.copygrinder.pure.copybean.model.Copybean
import org.copygrinder.pure.copybean.search.DocumentBuilder
import org.copygrinder.unpure.system.Configuration

class Indexer {

  protected lazy val config = wire[Configuration]

  protected lazy val documentBuilder = wire[DocumentBuilder]

  protected lazy val analyzer = new KeywordAnalyzer()

  protected lazy val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_9, analyzer)

  protected lazy val indexDirFile = FSDirectory.open(new File(config.indexRoot))

  protected lazy val indexWriter = new IndexWriter(indexDirFile, indexWriterConfig)

  protected lazy val indexReader = DirectoryReader.open(indexDirFile)

  protected lazy val indexSearcher = new IndexSearcher(indexReader)

  def addCopybean(copybean: Copybean): Unit = {
    val doc = documentBuilder.buildDocument(copybean)
    indexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def findCopybeanIds(field: String, phrase: String): Seq[String] = {
    val query = new PhraseQuery
    query.add(new Term(s"contains.$field", phrase))
    val docs = indexSearcher.search(query, config.indexMaxResults)
    val copybeanIds = docs.scoreDocs.map(scoreDoc => {
      val doc = indexReader.document(scoreDoc.doc)
      doc.get("id")
    })
    copybeanIds
  }


}
