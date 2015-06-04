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
package org.copygrinder.impure.copybean.persistence

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.ReifiedField.{ListReifiedField, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.persistence.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class CopybeanPersistenceService(
 idEncoderDecoder: IdEncoderDecoder,
 predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans,
 deltaCalculator: DeltaCalculator
 ) extends LazyLogging {



  def fetchCopybeansFromCommits(ids: Seq[String], commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    fetchFromCommit(ids, commitIds) {
      case (id, dataOpt) =>
        if (dataOpt.isEmpty) {
          throw new CopybeanNotFound(id)
        } else {
          dataOpt.get
        }
    }
  }

  protected def reifyBeans(copybeans: Seq[Copybean], commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    val commitsFuture = addInternalCommit(commitIds)

    commitsFuture.flatMap { commitIdsWithInternal =>

      val nestedFutures = copybeans.map(copybean => {
        resolveTypes(copybean, commitIdsWithInternal).map(types => {
          ReifiedCopybean(copybean, types)
        })
      })
      Future.sequence(nestedFutures)
    }
  }

  protected def addInternalCommit(commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope): Future[Seq[TreeCommit]] = {
    if (commitIds.exists(_.treeId == Trees.internal)) {
      Future {
        commitIds
      }
    } else {
      getCommitIdOfActiveHeadOfBranch(TreeBranch(Branches.master, Trees.internal)).map(v => commitIds :+ v)
    }
  }

  val typeEnforcer = new TypeEnforcer(new CopybeanTypeEnforcer())

  protected def resolveTypes(copybean: AnonymousCopybean, commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope): Future[Set[CopybeanType]] = {

    fetchCopybeanTypesFromCommits(copybean.enforcedTypeIds.toSeq, commitIds).map(_.toSet)
  }

  def findExpandableBeans(
   copybeans: Seq[ReifiedCopybean], expandableFields: Seq[String], commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope): Future[Map[String, ReifiedCopybean]] = {

    if (expandableFields.nonEmpty) {
      val expandAll = expandableFields.contains("*")

      val referenceFields = copybeans.flatMap(copybean => {
        copybean.reifiedFields.flatMap(field => {
          if (expandAll || expandableFields.contains("content." + field._1)) {
            field._2 match {
              case r: ReferenceReifiedField => Seq(Some(r))
              case l: ListReifiedField => l.castVal.map {
                case nestedRef: ReferenceReifiedField =>
                  Some(nestedRef)
                case _ =>
                  None
              }
              case _ => Seq(None)
            }
          } else {
            Seq(None)
          }
        }).flatten.toSet
      })

      val beansFuture = fetchCopybeansFromCommits(referenceFields.map(_.castVal), commitIds)

      beansFuture.map(beans => {
        referenceFields.map(field => {
          (field.fieldDef.id, beans.find(_.id == field.castVal).get)
        }).toMap
      })

    } else {
      Future(Map())
    }
  }

  def storeAnonBean(anonCopybeans: Seq[AnonymousCopybean], commit: CommitRequest)
   (implicit siloScope: SiloScope): Future[(String, Seq[ReifiedCopybean])] = {

    val copybeans = anonCopybeans.map(anonCopybean => {
      val id = idEncoderDecoder.encodeUuid(UUID.randomUUID())
      new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content)
    })

    storeBean(copybeans, commit)
  }

  protected def storeBean(rawCopybeans: Seq[Copybean], commit: CommitRequest)
   (implicit siloScope: SiloScope): Future[(String, Seq[ReifiedCopybean])] = {

    val commits = Seq(TreeCommit(commit.parentCommitId, commit.branchId.treeId))
    val newCopybeanFuture = reifyBeans(rawCopybeans, commits)

    newCopybeanFuture.flatMap(copybeans => {
      val beanAndDataFutures = copybeans.map(newBean => {
        typeEnforcer.enforceTypes(newBean, commits)(addInternalCommit, fetchCopybeansFromCommits).map(_ => {
          (newBean, CommitData(newBean.id, Some(newBean)))
        })
      })
      Future.sequence(beanAndDataFutures).flatMap(beanAndDataSeq => {
        val commitFuture = siloScope.persistor.commit(commit, beanAndDataSeq.map(_._2))
        commitFuture.map(commit => (commit.id, beanAndDataSeq.map(_._1)))
      })
    })
  }

  def findByCommit(commitIds: Seq[TreeCommit], params: Map[String, List[String]])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    val query = new Query(params)
    siloScope.persistor.query(commitIds, siloScope.defaultLimit, query)
  }


  def update(id: String, anonCopybean: AnonymousCopybean, commit: CommitRequest)
   (implicit siloScope: SiloScope): Future[String] = {

    val commits = Seq(TreeCommit(commit.parentCommitId, commit.branchId.treeId))
    val rawBean = Seq(new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content))

    val copybeanFuture = reifyBeans(rawBean, commits)

    copybeanFuture.flatMap(copybeans => {

      val copybean = copybeans.head

      typeEnforcer.enforceTypes(copybean, commits)(addInternalCommit, fetchCopybeansFromCommits).flatMap(_ => {
        val data = CommitData(copybean.id, Some(copybean))
        siloScope.persistor.commit(commit, Seq(data)).map(_.id)
      })

    })
  }

  def delete(id: String, commit: CommitRequest)(implicit siloScope: SiloScope): Future[String] = {
    val data = CommitData(id, None)
    siloScope.persistor.commit(commit, Seq(data)).map(_.id)
  }

  def createSilo()(implicit siloScope: SiloScope): Future[Commit] = {
    siloScope.persistor.initSilo().flatMap(_ => {

      val beans = predefinedCopybeans.predefinedBeans.values

      val reifiedBeans = beans.map(bean => {
        val types = bean.enforcedTypeIds.flatMap(cbType => predefinedCopybeanTypes.predefinedTypes.get(cbType))
        ReifiedCopybean(bean, types)
      })

      val beanObjs = reifiedBeans.map { bean =>
        new CommitData(bean.id, Some(bean))
      }.toSeq

      val types = predefinedCopybeanTypes.predefinedTypes.values.map { cbType =>
        new CommitData(cbType.id, Some(cbType))
      }

      val commit = new CommitRequest(TreeBranch(Branches.master, Trees.internal), "", "", "", None)
      siloScope.persistor.commit(commit, beanObjs ++ types)
    })
  }

  def getBranchesFromTrees(treeIds: Seq[String])(implicit siloScope: SiloScope): Future[Seq[TreeBranch]] = {
    siloScope.persistor.getBranches(treeIds)
  }

  def getCommitsByBranch(branchId: TreeBranch)(implicit siloScope: SiloScope): Future[Seq[Commit]] = {
    siloScope.persistor.getCommitsByBranch(branchId, siloScope.defaultLimit)
  }

  def getHistoryByIdAndCommits(id: String, commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[Commit]] = {
    siloScope.persistor.getHistoryByIdAndCommits(id, commitIds, siloScope.defaultLimit)
  }

  def getDeltaByIdAndCommit(id: String, commitId: TreeCommit)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Iterable[BeanDelta]] = {

    siloScope.persistor.getByIdsAndCommits(Seq(id), Seq(commitId)).flatMap { beanOpts =>
      val (bean, commit) = beanOpts.head.getOrElse(throw new CopybeanNotFound(id))

      val prevCommit = TreeCommit(commit.parentCommitId, commitId.treeId)
      siloScope.persistor.getByIdsAndCommits(Seq(id), Seq(prevCommit)).map { prevObjAndCommits =>
        val prevBean = prevObjAndCommits.head.map(_._1)
        deltaCalculator.calcBeanDeltas(prevBean, bean)
      }
    }
  }

  def getCommitIdOfActiveHeadOfBranch(branchId: TreeBranch)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[TreeCommit] = {

    val headsFuture = getBranchHeads(branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      //TODO: Implement real active branch head calculation
      val headOpt = heads.headOption
      val id = if (headOpt.isDefined) {
        headOpt.get.id
      } else {
        ""
      }

      TreeCommit(id, branchId.treeId)
    })

    activeHeadFuture
  }

  def getCommitIdOfActiveHeadOfBranches(branchIds: Seq[TreeBranch])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[TreeCommit]] = {
    val futures = branchIds.map { branchId =>
      getCommitIdOfActiveHeadOfBranch(branchId)
    }
    Future.sequence(futures)
  }


  def getBranchHeads(branchId: TreeBranch)
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[Commit]] = {
    siloScope.persistor.getBranchHeads(branchId)
  }

  protected def fetchFromCommit[T](ids: Seq[String], commitIds: Seq[TreeCommit])
   (func: (String, Option[ReifiedCopybean]) => T)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Seq[T]] = {

    val persistableObjsFuture = siloScope.persistor.getByIdsAndCommits(ids, commitIds)

    persistableObjsFuture.map { objs =>

      ids.zipWithIndex.map { case (id, index) =>
        func(id, objs(index).map(_._1))
      }
    }
  }

  def fetchCopybeanTypesFromCommits(ids: Seq[String], commitIds: Seq[TreeCommit])
   (implicit siloScope: SiloScope, ex: ExecutionContext): Future[Seq[CopybeanType]] = {

    val query = Query(Map("content.typeId" -> ids))
    siloScope.persistor.query(commitIds, siloScope.defaultLimit, query).map { beans =>
      val types = beans.map(CopybeanType(_))
      val typeMap = types.map(t => t.typeId -> t).toMap
      ids.foreach { id =>
        if (!typeMap.contains(id)) {
          throw new CopybeanTypeNotFound(id)
        }
      }
      types
    }

  }

}
