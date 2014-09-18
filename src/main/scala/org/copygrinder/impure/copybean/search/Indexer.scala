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

import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.analysis.core.KeywordAnalyzer
import org.apache.lucene.index._
import org.apache.lucene.search._
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType}
import org.copygrinder.pure.copybean.search.{DocumentBuilder, QueryBuilder}

class Indexer(indexDir: File, documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, defaultMaxResults: Int)
  extends LazyLogging {

  protected lazy val analyzer = new KeywordAnalyzer()

  protected lazy val indexDirectory = FSDirectory.open(indexDir)

  protected lazy val indexWriterConfig = new IndexWriterConfig(Version.LUCENE_4_10_0, analyzer)

  protected lazy val indexWriter = new IndexWriter(indexDirectory, indexWriterConfig)

  protected lazy val trackingIndexWriter = new TrackingIndexWriter(indexWriter)

  protected lazy val searcherManager = new SearcherManager(indexWriter, true, new SearcherFactory())

  protected lazy val indexRefresher = new ControlledRealTimeReopenThread[IndexSearcher](
    trackingIndexWriter, searcherManager, 60, 0
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
    logger.debug("Adding doc to index: " + doc)
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def findCopybeanIds(): Seq[String] = {
    val query = new MatchAllDocsQuery
    doQuery(query)
  }


  def findCopybeanIds(params: Seq[(String, String)]): Seq[String] = {
    val query = queryBuilder.build(params)
    doQuery(query)
  }


  protected def doQuery(query: Query): Seq[String] = {
    indexRefresher.waitForGeneration(reopenToken)
    val indexSearcher = searcherManager.acquire()
    try {
      val docs = indexSearcher.search(query, defaultMaxResults)
      val copybeanIds = docs.scoreDocs.map(scoreDoc => {
        val doc = indexSearcher.getIndexReader.document(scoreDoc.doc)
        doc.get("id")
      })
      copybeanIds
    } finally {
      searcherManager.release(indexSearcher)
    }
  }

  def addType(copybeanType: CopybeanType): Unit = {

  }

}
