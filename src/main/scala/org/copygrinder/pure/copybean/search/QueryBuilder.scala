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

import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search._

class QueryBuilder extends LazyLogging {


  def build(params: Seq[(String, String)], prefix: String = "contains."): Query = {
    val query = doBuild(params, prefix)
    logger.debug("Built Query " + query)
    query
  }

  protected def doBuild(params: Seq[(String, String)], prefix: String): Query = {
    val (paramHead, paramTail) = params.span(param => {
      val field = param._1.toLowerCase
      val operator = field == "or" || field == "and" || field == "not"
      !operator || (operator && param._2.nonEmpty)
    })
    val headQuery = createQuery(paramHead, prefix)
    if (paramTail.nonEmpty) {

      val clause = paramTail.head._1.toLowerCase match {
        case "and" => BooleanClause.Occur.MUST
        case "or" => BooleanClause.Occur.SHOULD
        case "not" => BooleanClause.Occur.MUST_NOT
      }

      val booleanQuery = new BooleanQuery()
      booleanQuery.add(headQuery, clause)
      booleanQuery.add(doBuild(paramTail.tail, prefix), clause)
      booleanQuery
    } else {
      headQuery
    }
  }


  protected def createQuery(params: Seq[(String, String)], prefix: String): Query = {
    val booleanQuery = new BooleanQuery()
    params.foreach { param =>
      if (param._1.nonEmpty && param._2.nonEmpty) {
        val (field, clause) = determineBooleanClause(param._1)
        val query = addParamToQuery(field, param._2, booleanQuery, prefix)
        booleanQuery.add(query, clause)
      }
    }
    booleanQuery
  }

  protected def determineBooleanClause(field: String): (String, Occur) = {
    if (field.endsWith("~")) {
      (field.dropRight(1), BooleanClause.Occur.SHOULD)
    } else if (field.endsWith("!")) {
      (field.dropRight(1), BooleanClause.Occur.MUST_NOT)
    } else {
      (field, BooleanClause.Occur.MUST)
    }
  }

  protected def addParamToQuery(field: String, value: String, booleanQuery: BooleanQuery, prefix: String): Query = {

    val namespace = determineNamespace(field, prefix)

    val scopedField = s"$namespace" + field

    value match {
      case intString if intString.forall(_.isDigit) => {
        val intValue = value.toInt
        val query = new BooleanQuery()
        val numericQuery = NumericRangeQuery.newIntRange(scopedField, 1, intValue, intValue, true, true)
        val termQuery = new TermQuery(new Term(scopedField, value))
        query.add(numericQuery, BooleanClause.Occur.SHOULD)
        query.add(termQuery, BooleanClause.Occur.SHOULD)
        query
      }
      case _ => {
        val q = new PhraseQuery
        q.add(new Term(scopedField, value))
        q
      }
    }
  }

  protected def determineNamespace(field:String, prefix:String):String = {
    if (field equalsIgnoreCase "enforcedTypeIds") {
      ""
    } else {
      prefix
    }
  }

}
