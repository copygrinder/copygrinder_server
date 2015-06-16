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

import org.copygrinder.pure.copybean.exception.MergeException
import org.copygrinder.pure.copybean.model.{ReifiedField, Commit, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence.model.BeanDelta

import scala.annotation.tailrec

class HistoryService {

  protected val deltaCalculator = new DeltaCalculator()

  case class DeltaCommitsResult(sourceDeltas: Set[String], targetDeltas: Set[String])

  /*def findDeltaCommitIds(sourceCommit: Commit, targetCommit: Commit): DeltaCommitsResult = {

    if (targetCommit.knownCommits.contains(sourceCommit.id)) {
      throw new MergeException(
        s"Source commit '${sourceCommit.id}' is a child of target commit '${targetCommit.id}' and can't be merged.")
    }

    val sourceDeltas = sourceCommit.changedIds -- targetCommit.changedIds
    val targetDeltas = targetCommit.changedIds -- sourceCommit.changedIds

    DeltaCommitsResult(sourceDeltas, targetDeltas)
  }

  case class DeltaRequest(sourceBeansChanged: Set[String], potentialConflictIds: Set[String], sourceBase: String,
   targetBase: String)

  def buildDeltaRequest(sourceCommit: Commit, targetCommit: Commit, sourceDeltas: Set[Commit],
   targetDeltas: Set[Commit]): DeltaRequest = {

    val sourceBeansChanged = sourceDeltas.flatMap(_.changedIds)
    val targetBeansChanged = targetDeltas.flatMap(_.changedIds)

    val potentialConflictIds = sourceBeansChanged.intersect(targetBeansChanged)

    val sourceDeltaMap = sourceDeltas.map(d => d.id -> d).toMap
    val targetDeltaMap = targetDeltas.map(d => d.id -> d).toMap

    val sourceBase = findBase(sourceDeltaMap, sourceCommit)
    val targetBase = findBase(targetDeltaMap, targetCommit)

    DeltaRequest(sourceBeansChanged, potentialConflictIds, sourceBase, targetBase)
  }

  @tailrec
  protected final def findBase(deltaMap: Map[String, Commit], focus: Commit): String = {

    val parentOpt = deltaMap.get(focus.parentCommitId)
    if (parentOpt.isEmpty) {
      focus.parentCommitId
    } else {
      findBase(deltaMap, parentOpt.get)
    }
  }

  case class CalcDeltaRequest(sourceBaseBeans: Map[String, ReifiedCopybean], sourceBeans: Map[String, ReifiedCopybean],
   targetBaseBeans: Map[String, ReifiedCopybean], targetBeans: Map[String, ReifiedCopybean])

  case class CalcDeltaResults(sourceDeltas: Map[String, Iterable[BeanDelta]],
   targetDeltas: Map[String, Iterable[BeanDelta]])

  def calcDeltas(deltaRequest: DeltaRequest, calcDeltaRequest: CalcDeltaRequest): CalcDeltaResults = {

    val sourceDeltas = doDeltaCalc(deltaRequest.sourceBeansChanged, calcDeltaRequest.sourceBaseBeans,
      calcDeltaRequest.sourceBeans)

    val targetDeltas = doDeltaCalc(deltaRequest.potentialConflictIds, calcDeltaRequest.targetBaseBeans,
      calcDeltaRequest.targetBeans)

    CalcDeltaResults(sourceDeltas, targetDeltas)
  }


  protected def doDeltaCalc(changeIds: Set[String], baseBeans: Map[String, ReifiedCopybean],
   currentBeans: Map[String, ReifiedCopybean]): Map[String, Iterable[BeanDelta]] = {
    changeIds.map { id =>
      val baseBean = baseBeans.get(id).get
      val currentBean = currentBeans.get(id).get
      id -> deltaCalculator.calcBeanDeltas(Option(baseBean), currentBean)
    }.toMap
  }

  def findPotentialConflicts(calcDeltaResults: CalcDeltaResults): Map[String, Map[String, ReifiedField]] = {
    calcDeltaResults.targetDeltas.map { case (id, targetDeltas) =>
      val sourceDeltasOpt = calcDeltaResults.sourceDeltas.get(id)
      if (sourceDeltasOpt.nonEmpty) {
        id -> Map.empty[String, ReifiedField]
      } else {

        val sourceDeltas = sourceDeltasOpt.get

        id -> findConflictFields(targetDeltas, sourceDeltas)
      }
    }.filter(_._2.nonEmpty)
  }

  protected def findConflictFields(targetDeltas: Iterable[BeanDelta], sourceDeltas: Iterable[BeanDelta]) = {
    val sourceDeltaFields = sourceDeltas.map(_.newField).map(field => field.fieldDef.id -> field).toMap
    val targetDeltaFields = targetDeltas.map(_.newField).map(field => field.fieldDef.id -> field).toMap

    val conflictFields = targetDeltaFields.filter { case (fieldId, targetField) =>
      val sourceFieldOpt = sourceDeltaFields.get(fieldId)
      if (sourceFieldOpt.isEmpty) {
        false
      } else {
        val sourceField = sourceFieldOpt.get
        sourceField.value != targetField.value
      }
    }

    conflictFields
  }

  def filterConflicts(potentialConflicts: Map[String, Map[String, ReifiedField]]) = {

  }*/
}