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
package org.copygrinder.impure.copybean.persistence.backend.impl

import java.io.File

import net.jpountz.xxhash.XXHashFactory
import org.copygrinder.impure.copybean.persistence.backend.{PersistentObjectSerializer, VersionedDataPersistor}
import org.copygrinder.pure.collections.ImmutableLinkedHashMap
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.{HashFactoryWrapper, DeltaCalculator, IdEncoderDecoder}
import org.copygrinder.pure.copybean.persistence.model._

import scala.concurrent.{ExecutionContext, Future}

class KeyValuePersistor(silo: String, storageDir: File, serializer: PersistentObjectSerializer[Array[Byte]])
 extends VersionedDataPersistor {

  protected val idEncoder = new IdEncoderDecoder()

  protected val dao = new MapDbDao(silo, storageDir)

  protected val deltaCalc = new DeltaCalculator

  def initSilo()(implicit ec: ExecutionContext): Future[Unit] = {
    dao.initSilo()
  }

  protected def fetchTypesFromCommitNodes(commitNodes: Seq[CommitNode])(typeIds: Set[String])
   (implicit ec: ExecutionContext): Future[Set[CopybeanType]] = {
    if (typeIds.nonEmpty) {
      val query = new Query(Map("content.typeId" -> typeIds.toSeq))
      doQuery(commitNodes, commitNodes.length, query).map(_.map { bean =>
        val cbType = CopybeanType(bean)
        cbType
      }.toSet)
    } else {
      Future(Set())
    }
  }

  protected def getCommits(commitIds: Seq[TreeCommit])
   (implicit ec: ExecutionContext): Future[Seq[(String, CommitNode)]] = {

    val treeIdAndCommits = commitIds.filter(_.id.nonEmpty).groupBy(_.treeId)

    val futures = treeIdAndCommits.map { case (treeId, commits) =>
      dao.getSeq[CommitNode](treeId, commits.map(_.id)).map(results => (treeId, results.values))
    }.toSeq

    Future.sequence(futures).map { values =>
      values.flatMap { treeAndCommitNodes =>
        treeAndCommitNodes._2.map { node =>
          (treeAndCommitNodes._1, node)
        }
      }
    }
  }

  def getByIdsAndCommits(ids: Seq[String], commitIds: Seq[TreeCommit])
   (implicit ec: ExecutionContext): Future[Seq[Option[(ReifiedCopybean, Commit)]]] = {
    getCommits(commitIds).flatMap { commits =>
      getByIdsAndCommitNodes(ids, commits.map(_._2))
    }
  }

  protected def getByIdsAndCommitNodes(ids: Seq[String], commits: Seq[CommitNode])
   (implicit ec: ExecutionContext): Future[Seq[Option[(ReifiedCopybean, Commit)]]] = {

    doGetByIdsAndCommitNodes(ids, commits) { byteArray =>
      serializer.deserialize(fetchTypesFromCommitNodes(commits), byteArray)
    }
  }

  protected def getCopybeanByIdsAndCommitNodes(ids: Seq[String], commits: Seq[CommitNode])
   (implicit ec: ExecutionContext): Future[Seq[Option[(Copybean, Commit)]]] = {

    doGetByIdsAndCommitNodes(ids, commits) { byteArray =>
      Future(serializer.deserializeCopybean(byteArray))
    }
  }

  protected def doGetByIdsAndCommitNodes[T](ids: Seq[String], commits: Seq[CommitNode])
   (func: (Array[Byte] => Future[T]))(implicit ec: ExecutionContext): Future[Seq[Option[(T, Commit)]]] = {

    val futures = ids.map { id =>
      val byteArrayOpt = commits.foldLeft(Option.empty[(Array[Byte], CommitNode)]) { (result, commit) =>
        if (result.isEmpty) {
          commit.byteStore.get(id).map((_, commit))
        } else {
          result
        }
      }

      if (byteArrayOpt.nonEmpty) {
        val (byteArray, commit) = byteArrayOpt.get
        func(byteArray).map { bean =>
          Option(bean, commitNodeToCommit(commit))
        }
      } else {
        Future(Option.empty[(T, Commit)])
      }
    }

    Future.sequence(futures)
  }

  def getHistoryByIdAndCommits(id: String, commitIds: Seq[TreeCommit], limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]] = {

    val prevCommitsFuture = getPreviousCommitNodes(ImmutableLinkedHashMap(), commitIds, limit)

    prevCommitsFuture.map { prevCommits =>
      val relevantCommits = prevCommits.filter { prevCommit =>
        prevCommit.changedIds.beanIdToFieldIdToDeltaHash.contains(id)
      }
      relevantCommits.map(commitNodeToCommit)
    }

  }

  def getBranchHeads(branchId: TreeBranch)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    dao.getCompositeOpt[Set[Commit]]("heads", (branchId.treeId, branchId.id)).map { headsOpt =>
      headsOpt.getOrElse(Set()).toSeq
    }
  }

  protected final def getPreviousCommits(commits: Seq[TreeCommit], limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    getPreviousCommitNodes(ImmutableLinkedHashMap(), commits, limit).map(_.map(commitNodeToCommit))
  }

  protected final def getPreviousCommitNodes(results: ImmutableLinkedHashMap[String, CommitNode],
   commits: Seq[TreeCommit], limit: Int)(implicit ec: ExecutionContext): Future[Seq[CommitNode]] = {

    val commitNodesFuture = getCommits(commits)
    commitNodesFuture.flatMap { commitNodes =>

      val newResults = results ++ commitNodes.map(_._2).map(n => (n.id, n))

      val previousCommitIds = commitNodes
       .filterNot(c => newResults.contains(c._2.previousCommitId))
       .map(c => TreeCommit(c._2.previousCommitId, c._1))
       .distinct

      if (newResults.size >= limit || previousCommitIds.isEmpty) {
        Future {
          newResults.values.take(limit).toSeq
        }
      } else {
        getPreviousCommitNodes(newResults, previousCommitIds, limit)
      }
    }
  }

  protected def commitNodeToCommit(node: CommitNode): Commit = {
    Commit(node.id, node.branchId, node.previousCommitId, "", node.mergeData, node.changedIds, node.knownCommits)
  }

  def query(commitIds: Seq[TreeCommit], limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[ReifiedCopybean]] = {

    val commitsFuture = getCommits(commitIds)

    commitsFuture.flatMap { commitNodes =>
      doQuery(commitNodes.map(_._2), limit, query)
    }
  }

  protected def doQuery(commits: Seq[CommitNode], limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[ReifiedCopybean]] = {

    val ids = commits.flatMap { commitNode =>
      commitNode.byteStore.keys
    }

    val beansFuture = getCopybeanByIdsAndCommitNodes(ids.toSeq, commits).map(_.flatten).map(objSeq => {
      objSeq.map(_._1).filter(bean => {
        query.fieldsAndValues.forall { case (fieldId, values) =>
          queryBean(fieldId, values, bean)
        }
      }).take(limit)
    })

    beansFuture.flatMap { beans =>
      val futures = beans.map { bean =>
        serializer.reifyBean(bean, fetchTypesFromCommitNodes(commits))
      }
      Future.sequence(futures)
    }

  }

  protected def queryBean(fieldId: String, values: Seq[String], copybean: Copybean): Boolean = {

    val (left, right) = splitFirstDot(fieldId)

    if (left == "content" && right.nonEmpty) {
      values.exists(value => {
        val contentOpt = copybean.content.get(right)
        if (contentOpt.isDefined) {
          val hit = contentOpt.get.toString == value
          hit
        } else {
          false
        }
      })
    } else if (fieldId == "enforcedTypeIds") {
      values.exists(value => {
        copybean.enforcedTypeIds.contains(value)
      })
    } else {
      throw new CopygrinderRuntimeException("Unknown query field: " + fieldId)
    }

  }

  protected def queryType(fieldId: String, values: Seq[String], copybeanType: CopybeanType): Boolean = {

    if (fieldId == "id") {
      values.contains(copybeanType.id)
    } else {
      true
    }

  }

  def commit(request: CommitRequest, datas: Seq[CommitData])(implicit ec: ExecutionContext): Future[Commit] = {

    val treeId = request.branchId.treeId
    val branchId = request.branchId.id
    val parentCommitId = request.parentCommitId

    dao.getCompositeOpt[Set[Commit]]("heads", (treeId, branchId)).flatMap { headsOpt =>

      dao.getOpt[CommitNode](treeId, parentCommitId).flatMap { previousCommitOpt =>

        val previousCommit = if (previousCommitOpt.isEmpty) {
          if (request.parentCommitId.isEmpty) {
            new CommitNode("", "", "", None, Map(), CommitChange(), Set(""))
          } else {
            throw new BadParent("Parent Commit doesn't exist: " + parentCommitId)
          }
        } else {
          previousCommitOpt.get
        }

        createNewByteStore(datas, previousCommit, treeId).flatMap { newByteStore =>
          doActualCommit(request, previousCommit, datas, headsOpt, newByteStore)
        }
      }

    }

  }

  protected def doActualCommit(request: CommitRequest, previousCommit: CommitNode, datas: Seq[CommitData],
   headsOpt: Option[Set[Commit]], newByteStore: Map[String, Array[Byte]])
   (implicit ec: ExecutionContext): Future[Commit] = {

    val treeId = request.branchId.treeId
    val branchId = request.branchId.id
    val parentCommitId = request.parentCommitId

    val newHash: String = buildNewHash(request, newByteStore)

    val changedIds = datas.map(data => data.id)
    val newBeansMap = datas.filter(_.obj.isDefined).map(data => data.id -> data.obj.get).toMap

    getByIdsAndCommits(changedIds, Seq(TreeCommit(parentCommitId, treeId))).flatMap { oldBeans =>

      val oldBeansMap = oldBeans.flatten.map { case (bean, _) =>
        bean.id -> bean
      }.toMap

      val commitChange = deltaCalc.calcBeanCommitDeltas(oldBeansMap, newBeansMap)

      val mergeData = request.mergeRequestOpt.map { mergeReq =>
        MergeData(mergeReq.mergeParentId, mergeReq.excludedIds)
      }

      val newKnownCommits1 = previousCommit.knownCommits + newHash
      val newKnownCommits2 = mergeData.fold(newKnownCommits1)(newKnownCommits1 + _.mergedCommitId)

      val newCommitNode = new CommitNode(newHash, branchId, parentCommitId, mergeData, newByteStore, commitChange,
        newKnownCommits2)
      val newCommit = commitNodeToCommit(newCommitNode)

      val newHeads = buildNewHeads(parentCommitId, headsOpt, newCommit)

      dao.setAndThen(treeId, newHash, newCommitNode) {
        dao.setComposite("heads", (treeId, newCommit.branchId), newHeads)
      }.map { _ =>
        newCommit
      }
    }
  }

  protected def buildNewHeads(parentCommitId: String, headsOpt: Option[Set[Commit]], newCommit: Commit): Set[Commit] = {
    val existingHeads = headsOpt.getOrElse(Set())
    val newHeads = existingHeads.filter(_.id != parentCommitId) + newCommit

    if (existingHeads.nonEmpty && newHeads.size > existingHeads.size) {
      throw new MultipleHeadsException(newCommit.branchId)
    }

    newHeads
  }

  protected def buildNewHash(request: CommitRequest, newByteStore: Map[String, Array[Byte]]): String = {
    val newHashBuilder = HashFactoryWrapper.newHash()
    newByteStore.values.foreach(byteArray => newHashBuilder.update(byteArray, 0, byteArray.length))
    val parentCommitAsByteArray = request.parentCommitId.getBytes("UTF-8")
    newHashBuilder.update(parentCommitAsByteArray, 0, parentCommitAsByteArray.size)

    idEncoder.encodeLong(newHashBuilder.getValue)
  }

  protected def createNewByteStore(datas: Seq[CommitData], previousCommit: CommitNode, treeId: String)
   (implicit ec: ExecutionContext) = {

    datas.foldLeft(Future(previousCommit.byteStore)) {
      case (result, data) =>

        result.flatMap(resultByteStore => {

          if (data.obj.nonEmpty) {
            val existingBytesOpt = previousCommit.byteStore.get(data.id)
            if (existingBytesOpt.nonEmpty) {
              handleUpdateCommit(data, resultByteStore, previousCommit, existingBytesOpt.get)
            } else {
              Future {
                handleNewCommit(data.id, resultByteStore, data.obj.get)
              }
            }
          } else {
            Future {
              handleDeleteCommit(data.id, resultByteStore)
            }
          }

        })
    }
  }

  protected def handleNewCommit(spaceAndId: String, prevByteStore: Map[String, Array[Byte]],
   obj: ReifiedCopybean): Map[String, Array[Byte]] = {
    val json = serializer.serialize(obj)
    prevByteStore + (spaceAndId -> json)
  }

  protected def handleUpdateCommit(
   data: CommitData, resultByteStore: Map[String, Array[Byte]], previousCommit: CommitNode, existingBytes: Array[Byte])
   (implicit ec: ExecutionContext): Future[Map[String, Array[Byte]]] = {

    val spaceAndId = data.id
    val obj = data.obj.get

    val json = serializer.serialize(obj)
    Future {
      resultByteStore.updated(spaceAndId, json)
    }
  }

  protected def handleDeleteCommit(spaceAndId: String, prevByteStore: Map[String, Array[Byte]]
   ): Map[String, Array[Byte]] = {
    prevByteStore - spaceAndId
  }

  override def getBranches(treeIds: Seq[String])(implicit ec: ExecutionContext): Future[Seq[TreeBranch]] = {
    val futures = treeIds.map { treeId =>
      dao.getCompositeKeySet("heads", treeId).map { branches =>
        branches.map { branchId =>
          TreeBranch(branchId, treeId)
        }.toSeq
      }
    }
    Future.sequence(futures).map(_.flatten)
  }

  protected def splitFirstDot(id: String) = {
    val (left, right) = id.splitAt(id.indexOf('.'))
    (left, right.drop(1))
  }

  def getHistoryByCommits(commits: Seq[TreeCommit], limit: Int)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    getPreviousCommits(commits, limit)
  }
}

protected case class CommitNode(id: String, branchId: String, previousCommitId: String, mergeData: Option[MergeData],
 byteStore: Map[String, Array[Byte]], changedIds: CommitChange, knownCommits: Set[String])

