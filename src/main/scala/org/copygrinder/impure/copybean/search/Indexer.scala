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

import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.copygrinder.impure.system.Configuration
import org.copygrinder.pure.copybean.model.Copybean
import org.copygrinder.pure.copybean.search.DocumentBuilder

class Indexer(config: Configuration, documentBuilder: DocumentBuilder) {

  protected lazy val analyzer = new KeywordAnalyzer()

  protected lazy val indexDirectory = FSDirectory.open(new File(config.indexRoot))

  protected lazy val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_10_0, analyzer)

  protected lazy val indexWriter = new IndexWriter(indexDirectory, indexWriterConfig)

  protected lazy val trackingIndexWriter = new TrackingIndexWriter(indexWriter)

  protected lazy val searcherManager = new SearcherManager(indexWriter, true, new SearcherFactory())

  protected lazy val indexRefresher = new ControlledRealTimeReopenThread[IndexSearcher](
    trackingIndexWriter, searcherManager, 60.00, 0.1
  )

  protected var reopenToken = 0L

  indexRefresher.start()

  protected def close() = {
    indexRefresher.interrupt()
    indexRefresher.close()

    indexWriter.commit()
    indexWriter.close()
  }

  def addCopybean(copybean: Copybean): Unit = {
    val doc = documentBuilder.buildDocument(copybean)
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def findCopybeanIds(): Seq[String] = {
    val query = new MatchAllDocsQuery
    doQuery(query)
  }


  def findCopybeanIds(params: Seq[(String, String)]): Seq[String] = {
    val booleanQuery = new BooleanQuery
    params.foreach { param =>
      if (param._1.nonEmpty && param._2.nonEmpty) {
        addParamToQuery(param, booleanQuery)
      }
    }
    doQuery(booleanQuery)
  }

  def addParamToQuery(param: (String, String), booleanQuery: BooleanQuery): Unit = {
    val field = s"contains." + param._1
    val value = param._2

    val query = if (value.nonEmpty && value.forall(_.isDigit)) {
      val intValue = value.toInt
      NumericRangeQuery.newIntRange(field, 1, intValue, intValue, true, true)
    } else {
      val q = new PhraseQuery
      q.add(new Term(field, value))
      q
    }
    booleanQuery.add(query, BooleanClause.Occur.MUST)
  }

  def doQuery(query: Query): Seq[String] = {
    indexRefresher.waitForGeneration(reopenToken)
    val indexSearcher = searcherManager.acquire()
    try {
      val docs = indexSearcher.search(query, config.indexMaxResults)
      val copybeanIds = docs.scoreDocs.map(scoreDoc => {
        val doc = indexSearcher.getIndexReader.document(scoreDoc.doc)
        doc.get("id")
      })
      copybeanIds
    } finally {
      searcherManager.release(indexSearcher)
    }
  }


}
