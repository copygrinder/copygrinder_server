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
package org.copygrinder.pure.copybean.persistence

import org.copygrinder.UnitTest
import org.copygrinder.pure.copybean.model.Commit
import org.copygrinder.pure.copybean.persistence.model.MergeData

class HistoryServiceTest extends UnitTest {

  /*val historyService = new HistoryService()

  protected def makeMap(commits: Seq[Commit]) = {
    commits.map { c =>
      c.id -> c
    }.toMap
  }

  val baseCommit = new Commit("", "", "", "", None, Set(), Set(""))

  protected def node(id: String, parent: String, known: Set[String] = Set(), mergeId: String = "",
   excludes: Set[String] = Set()) = {

    val result = baseCommit.copy(id, parentCommitId = parent, knownCommits = known ++ Seq(id, "", parent, mergeId))

    if (mergeId.nonEmpty) {
      result.copy(mergeData = Option(MergeData(mergeId, excludes)))
    } else {
      result
    }
  }

  val simpleBranchGraph = {
    // 1--3
    //  \
    //   2--4

    makeMap(Seq(
      node("1", ""),
      node("2", "1"),
      node("3", "1"),
      node("4", "2", Set("1"))
    ))
  }

  "historyService" should "get the LCA for a simple graph" in {
    checkScenario(simpleBranchGraph, "3", "4", "1")
    checkScenario(simpleBranchGraph, "1", "4", "1")
    checkScenario(simpleBranchGraph, "1", "2", "1")
    checkScenario(simpleBranchGraph, "1", "1", "1")
    checkScenario(simpleBranchGraph, "1", "3", "1")
  }

  it should "should handle when the common ancestor is the root" in {
    val result = historyService.findLca("1", "", simpleBranchGraph)
    assert(result.isEmpty)
  }

  val simpleMergeGraph = {
    // 1--3---5--6
    //  \    /
    //   2--4--7

    makeMap(Seq(
      node("5", "3", Set("1", "2"), "4"),
      node("6", "5", Set("1", "2", "3", "4")),
      node("7", "4", Set("1", "2"))
    )) ++ simpleBranchGraph
  }

  it should "get the LCA for a simple merge graph" in {
    checkScenario(simpleMergeGraph, "6", "7", "4")
    checkScenario(simpleMergeGraph, "6", "4", "4")
    checkScenario(simpleMergeGraph, "6", "2", "1")
  }

  val doubleMergeGraph = {
    // 1--3---5--6--10
    //  \    /    \
    //   2--4--7---8--9

    makeMap(Seq(
      node("8", "7", Set("1", "2", "3", "4", "5"), "6"),
      node("9", "8", Set("1", "2", "3", "4", "5", "6", "7")),
      node("10", "6", Set("1", "2", "3", "4", "5"))
    )) ++ simpleMergeGraph
  }

  it should "get the LCA for a double merge graph" in {
    checkScenario(doubleMergeGraph, "10", "9", "6")
    checkScenario(doubleMergeGraph, "10", "7", "4")
    checkScenario(doubleMergeGraph, "10", "2", "1")
  }


  val complexMergeGraph = {
    // 1--3---5--6--10-----13
    //  \    /    \        /
    //   2--4--7---8--9   /
    //          \        /
    //          11------12

    makeMap(Seq(
      node("11", "7", Set("1", "2", "4")),
      node("12", "11", Set("1", "2", "4", "7")),
      node("13", "10", Set("1", "2", "3", "4", "5", "6", "7", "11"), "12")
    )) ++ doubleMergeGraph
  }

  it should "get the LCA for a complex merge graph" in {
    checkScenario(complexMergeGraph, "9", "13", "6")
    checkScenario(complexMergeGraph, "8", "13", "6")
    checkScenario(complexMergeGraph, "7", "13", "7")
  }

  val complexMergeGraph2 = {
    // 1--3---5--6--10---------13
    //  \    /                 /
    //   2--4--7---8--9       /
    //          \            /
    //          11--12--14--15

    makeMap(Seq(
      node("14", "12", Set("1", "2", "4", "7", "11")),
      node("15", "14", Set("1", "2", "4", "7", "11", "12"))
    )) ++ complexMergeGraph
     .updated("8", node("8", "7", Set("1", "2", "4")))
     .updated("9", node("9", "8", Set("1", "2", "4", "7")))
     .updated("13", node("13", "10", Set("1", "2", "3", "4", "5", "6", "7", "11", "12", "14"), "15"))
  }

  it should "get the LCA for an extra complex merge graph" in {
    checkScenario(complexMergeGraph2, "9", "13", "7")
  }

  protected def checkScenario(graph: Map[String, Commit], sourceId: String, targetId: String, expectedLca: String) = {
    val result = historyService.findLca(sourceId, targetId, graph).get
    assert(result.size == 1)
    assert(result.head._1.isEmpty)
    assert(result.head._2 == expectedLca)

    val result2 = historyService.findLca(targetId, sourceId, graph).get
    assert(result2.size == 1)
    assert(result2.head._1.isEmpty)
    assert(result2.head._2 == expectedLca)
  }*/

}