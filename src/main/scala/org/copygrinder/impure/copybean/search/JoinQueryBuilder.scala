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

import org.apache.lucene.search.BooleanClause.Occur
import org.apache.lucene.search.join.{JoinUtil, ScoreMode}
import org.apache.lucene.search.{BooleanQuery, IndexSearcher, Query}
import org.copygrinder.pure.copybean.search.QueryBuilder

class JoinQueryBuilder(queryBuilder: QueryBuilder) {

  def doJoinQuery(params: Seq[(String, String)], searcher: IndexSearcher): Query = {

    val (joinParams, nonJoinParams) = params.partition(param => param._1.startsWith("type."))

    val nonJoinQuery = queryBuilder.build(nonJoinParams)

    if (joinParams.nonEmpty) {
      val renamedParams = joinParamRenamer(joinParams)
      val fromQuery = queryBuilder.build(renamedParams, "")

      val joinQuery = JoinUtil.createJoinQuery("types.id", false, "enforcedTypeIds", fromQuery, searcher, ScoreMode.None)

      if (nonJoinParams.nonEmpty) {
        val booleanQuery = new BooleanQuery(true)
        booleanQuery.add(joinQuery, Occur.MUST)
        booleanQuery.add(nonJoinQuery, Occur.MUST)
        booleanQuery
      } else {
        joinQuery
      }
    } else {
      nonJoinQuery
    }
  }

  protected def joinParamRenamer(params: Seq[(String, String)]): Seq[(String, String)] = {

    params.map(param => {
      if (param._1.startsWith("type.")) {
        (param._1.replace("type.", "types."), param._2)
      } else {
        param
      }
    })

  }

}
