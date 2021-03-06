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
import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType}
import org.copygrinder.pure.copybean.search.{DocTypes, DocumentBuilder, QueryBuilder}

class Indexer(indexDir: File, documentBuilder: DocumentBuilder, queryBuilder: QueryBuilder, defaultMaxResults: Int)
 extends LazyLogging {

  protected lazy val analyzer = new KeywordAnalyzer()

  protected lazy val indexDirectory = FSDirectory.open(indexDir.toPath)

  protected lazy val indexWriterConfig = new IndexWriterConfig(analyzer)

  protected lazy val indexWriter = new IndexWriter(indexDirectory, indexWriterConfig)

  protected lazy val trackingIndexWriter = new TrackingIndexWriter(indexWriter)

  protected lazy val searcherManager = new SearcherManager(indexWriter, true, new SearcherFactory())

  protected val indexRefreshTime = 60

  //TODO: This keeps a thread running per silo scope.  We should probably just manually refresh the index on change.
  protected lazy val indexRefresher = new ControlledRealTimeReopenThread[IndexSearcher](
    trackingIndexWriter, searcherManager, indexRefreshTime, 0
  )

  protected lazy val indexUpgrader = new IndexUpgrader(indexDirectory)

  protected var reopenToken = 0L

  def upgrade(): Boolean = {
    try {
      indexUpgrader.upgrade()
      true
    } catch {
      case e@(_: IllegalArgumentException | _: IndexNotFoundException) => {
        false
      }
    }
  }

  def init(): Unit = {
    indexRefresher.start()
  }

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

  def updateCopybean(copybean: Copybean): Unit = {
    val doc = documentBuilder.buildDocument(copybean)
    deleteCopybean(copybean.id)
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def deleteCopybean(id: String): Unit = {
    reopenToken = trackingIndexWriter.deleteDocuments(new Term("id", id))
    indexWriter.commit()
  }

  def findCopybeanIds(): Seq[String] = {
    val query = NumericRangeQuery.newIntRange("doctype", 1, DocTypes.Copybean.id, DocTypes.Copybean.id, true, true)
    doQuery(query)
  }

  def findCopybeanIds(params: Seq[(String, String)]): Seq[String] = {
    val joinQueryBuilder = new JoinQueryBuilder(queryBuilder)
    val indexSearcher = searcherManager.acquire()
    try {
      val joinQuery = joinQueryBuilder.doJoinQuery(params, indexSearcher)
      doQuery(joinQuery)
    } finally {
      searcherManager.release(indexSearcher)
    }
  }

  protected def doQuery(query: Query): Seq[String] = {
    indexRefresher.waitForGeneration(reopenToken)
    val indexSearcher = searcherManager.acquire()
    try {
      val docs = indexSearcher.search(query, defaultMaxResults)
      logger.debug("Query found " + docs.scoreDocs.toList)
      val ids = docs.scoreDocs.map(scoreDoc => {
        val doc = indexSearcher.getIndexReader.document(scoreDoc.doc)
        doc.get("id")
      })
      ids
    } finally {
      searcherManager.release(indexSearcher)
    }
  }

  def addCopybeanType(copybeanType: CopybeanType): Unit = {
    val doc = documentBuilder.buildDocument(copybeanType)
    logger.debug("Adding doc: " + doc)
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def updateCopybeanType(copybeanType: CopybeanType): Unit = {
    val doc = documentBuilder.buildDocument(copybeanType)
    trackingIndexWriter.deleteDocuments(new Term("id", copybeanType.id))
    reopenToken = trackingIndexWriter.addDocument(doc)
    indexWriter.commit()
  }

  def findCopybeanTypeIds(): Seq[String] = {
    val query = NumericRangeQuery.newIntRange("doctype", 1, DocTypes.CopybeanType.id, DocTypes.CopybeanType.id, true, true)
    doQuery(query)
  }

  def findCopybeanTypeIds(params: Seq[(String, String)]): Seq[String] = {
    val query = queryBuilder.build(params, DocTypes.CopybeanType)
    doQuery(query)
  }

}
