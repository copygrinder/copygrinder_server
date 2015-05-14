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
import org.copygrinder.pure.copybean.model.{Commit, CopybeanType, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder
import org.copygrinder.pure.copybean.persistence.model._

import scala.concurrent.{ExecutionContext, Future, blocking}

class KeyValuePersistor(silo: String, storageDir: File, serializer: PersistentObjectSerializer[Array[Byte]])
 extends VersionedDataPersistor {

  protected val hashFactory = XXHashFactory.fastestInstance()

  protected val idEncoder = new IdEncoderDecoder()

  protected val dao = new MapDbDao(silo, storageDir)

  def initSilo()(implicit ec: ExecutionContext): Future[Unit] = {
    dao.initSilo()
  }

  protected def fetchTypesFromCommitNodes(commitNodes: Seq[CommitNode])(typeIds: Set[String])
   (implicit ec: ExecutionContext): Future[Set[CopybeanType]] = {
    val typesFuture = getByIdsAndCommitNodes(typeIds.toSeq.map(typeId => (Namespaces.cbtype, typeId)), commitNodes)
    typesFuture.map(obj => {
      obj.flatten.map(_.cbType).toSet
    })
  }

  protected def getCommits(commitIds: Seq[TreeCommit])(implicit ec: ExecutionContext): Future[Seq[CommitNode]] = {

    val treeIdAndCommits = commitIds.filter(_.id.nonEmpty).groupBy(_.treeId)

    val futures = treeIdAndCommits.map { case (treeId, commits) =>
      dao.getSeq[CommitNode](treeId, commits.map(_.id)).map(_.values)
    }.toSeq

    Future.sequence(futures).map(_.flatten)
  }

  def getByIdsAndCommits(ids: Seq[(String, String)], commitIds: Seq[TreeCommit])
   (implicit ec: ExecutionContext): Future[Seq[Option[PersistableObject]]] = {
    getCommits(commitIds).flatMap {
      commits =>
        getByIdsAndCommitNodes(ids, commits)
    }
  }

  protected def getByIdsAndCommitNodes(ids: Seq[(String, String)], commits: Seq[CommitNode])
   (implicit ec: ExecutionContext): Future[Seq[Option[PersistableObject]]] = {

    val futures = ids.map {
      namespaceAndId =>
        getByIdAndCommitNodes(commits, namespaceAndId)
    }

    Future.sequence(futures)
  }

  protected def getByIdAndCommitNodes(commits: Seq[CommitNode], namespaceAndId: (String, String))
   (implicit ec: ExecutionContext): Future[Option[PersistableObject]] = {
    val byteArrayOpt = commits.foldLeft(Option.empty[Array[Byte]]) {
      (result, commit) =>
        if (result.isEmpty) {
          blocking {
            commit.byteStore.get(resolveId(namespaceAndId))
          }
        } else {
          result
        }
    }

    if (byteArrayOpt.nonEmpty) {
      serializer.deserialize(namespaceAndId._1, fetchTypesFromCommitNodes(commits), byteArrayOpt.get).map(Option(_))
    } else {
      Future(Option.empty[PersistableObject])
    }
  }

  def getHistoryByIdAndCommits(id: (String, String), commitIds: Seq[TreeCommit], limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]] = {

    val prevCommitsFuture = getPreviousCommits(ImmutableLinkedHashMap(), commitIds, commitIds.head.treeId, limit)

    prevCommitsFuture.map {
      prevCommits =>
        val relevantCommits = prevCommits.filter {
          prevCommit =>
            true
        }
        relevantCommits
    }

  }

  def getBranchHeads(branchId: TreeBranch)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    dao.getCompositeOpt[Set[Commit]]("heads", (branchId.treeId, branchId.id)).map { headsOpt =>
      headsOpt.getOrElse(Set()).toSeq
    }
  }

  def getCommitsByBranch(branchId: TreeBranch, limit: Int)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    getBranchHeads(branchId).flatMap {
      commits =>
        val commitIds = commits.map(c => TreeCommit(c.id, branchId.treeId))
        getPreviousCommits(ImmutableLinkedHashMap[String, Commit](), commitIds, branchId.treeId, limit)
    }
  }

  protected final def getPreviousCommits(
   results: ImmutableLinkedHashMap[String, Commit], commits: Seq[TreeCommit], treeId: String, limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    getPreviousCommitNodes(ImmutableLinkedHashMap(), commits, treeId, limit).map(_.map(commitNodeToCommit))
  }

  protected final def getPreviousCommitNodes(
   results: ImmutableLinkedHashMap[String, CommitNode], commits: Seq[TreeCommit], treeId: String, limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[CommitNode]] = {

    val commitNodesFuture = getCommits(commits)
    commitNodesFuture.flatMap {
      commitNodes =>

        val newResults = results ++ commitNodes.map(n => (n.id, n))

        val previousCommitIds = commitNodes
         .filterNot(c => newResults.contains(c.previousCommitId))
         .map(c => TreeCommit(c.previousCommitId, treeId))
         .distinct

        if (newResults.size >= limit || previousCommitIds.isEmpty) {
          Future {
            newResults.values.take(limit).toSeq
          }
        } else {
          getPreviousCommitNodes(newResults, previousCommitIds, treeId, limit)
        }
    }
  }

  protected def commitNodeToCommit(node: CommitNode): Commit = {
    Commit(node.id, node.branchId, node.previousCommitId, "")
  }

  def query(commitIds: Seq[TreeCommit], limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[PersistableObject]] = {

    val commitsFuture = getCommits(commitIds)

    val commitsAndIdsFuture = commitsFuture.map {
      commitNodes =>
        val ids = commitNodes.flatMap {
          commitNode =>

            val allIds = commitNode.byteStore.keys.map(splitId(_))

            if (query.namespaceRestriction.isDefined) {
              allIds.filter(_._1 == query.namespaceRestriction.get)
            } else {
              allIds
            }
        }
        (commitNodes, ids)
    }

    commitsAndIdsFuture.flatMap {
      case (commits, ids) =>
        doQuery(commits, limit, ids, query)
    }
  }

  protected def doQuery(commits: Seq[CommitNode], limit: Int, ids: Iterable[(String, String)], query: Query)
   (implicit ec: ExecutionContext) = {
    getByIdsAndCommitNodes(ids.toSeq, commits).map(_.flatten).map(objSeq => {
      objSeq.filter(obj => {
        query.fieldsAndValues.forall {
          case ((namespace, fieldId), values) =>
            namespace match {
              case Namespaces.bean =>
                if (obj.beanOrType.isLeft) {
                  queryBean(fieldId, values, obj.bean)
                } else {
                  true
                }
              case Namespaces.cbtype =>
                if (obj.beanOrType.isRight) {
                  queryType(fieldId, values, obj.cbType)
                } else {
                  true
                }
              case other => throw new CopygrinderRuntimeException("Unknown namespace: " + other)
            }
        }
      }).take(limit)
    })
  }

  protected def queryBean(fieldId: String, values: Seq[String], reifiedCopybean: ReifiedCopybean): Boolean = {

    val (left, right) = splitFirstDot(fieldId)

    if (left == "content" && right.nonEmpty) {
      values.exists(value => {
        val contentOpt = reifiedCopybean.content.get(right)
        if (contentOpt.isDefined) {
          val hit = contentOpt.get.toString == value
          hit
        } else {
          false
        }
      })
    } else if (fieldId == "enforcedTypeIds") {
      values.exists(value => {
        reifiedCopybean.enforcedTypeIds.contains(value)
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

    dao.getOpt[CommitNode](request.branchId.treeId, request.parentCommitId).flatMap { previousCommitOpt =>

      val previousCommit = if (previousCommitOpt.isEmpty) {
        if (request.parentCommitId.isEmpty) {
          new CommitNode("", "", "", Map())
        } else {
          throw new BadParent("Parent Commit doesn't exist: " + request.parentCommitId)
        }
      } else {
        previousCommitOpt.get
      }

      createNewByteStore(datas, previousCommit, request.branchId.treeId).flatMap { newByteStore =>
        val newHash: String = buildNewHash(request, newByteStore)

        val newCommitNode = new CommitNode(newHash, request.branchId.id, request.parentCommitId, newByteStore)

        dao.set(request.branchId.treeId, newHash, newCommitNode).flatMap { _ =>

          val newCommit = new Commit(newHash, request.branchId.id, request.parentCommitId, "")

          updateHeads(request.branchId.treeId, newCommit).map { _ =>
            newCommit
          }
        }

      }
    }

  }

  protected def updateHeads(treeId: String, newCommit: Commit)(implicit ec: ExecutionContext): Future[Unit] = {

    dao.getCompositeOpt[Set[Commit]]("heads", (treeId, newCommit.branchId)).map { headsOpt =>
      val existingHeads = headsOpt.getOrElse(Set())
      val newHeads = existingHeads.filter(_.id != newCommit.parentCommitId) + newCommit
      dao.setComposite("heads", (treeId, newCommit.branchId), newHeads)
    }

  }

  protected val seed = 9283923842393L

  protected def buildNewHash(request: CommitRequest, newByteStore: Map[String, Array[Byte]]): String = {
    val newHashBuilder = hashFactory.newStreamingHash64(seed)
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
            val existingBytesOpt = previousCommit.byteStore.get(resolveId(data.id))
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

  protected def resolveId(id: (String, String)) = {
    id._1 + "." + id._2
  }

  protected def splitId(id: String) = {
    splitFirstDot(id)
  }

  protected def splitFirstDot(id: String) = {
    val (left, right) = id.splitAt(id.indexOf('.'))
    (left, right.drop(1))
  }

  protected def handleNewCommit(spaceAndId: (String, String), prevByteStore: Map[String, Array[Byte]],
   obj: PersistableObject): Map[String, Array[Byte]] = {
    val json = serializer.serialize(obj)
    prevByteStore + (resolveId(spaceAndId) -> json)
  }

  protected def handleUpdateCommit(
   data: CommitData, resultByteStore: Map[String, Array[Byte]], previousCommit: CommitNode, existingBytes: Array[Byte])
   (implicit ec: ExecutionContext): Future[Map[String, Array[Byte]]] = {

    val spaceAndId = data.id
    val obj = data.obj.get

    val json = serializer.serialize(obj)
    Future {
      resultByteStore.updated(resolveId(spaceAndId), json)
    }
  }

  protected def handleDeleteCommit(spaceAndId: (String, String), prevByteStore: Map[String, Array[Byte]]
   ): Map[String, Array[Byte]] = {
    prevByteStore - resolveId(spaceAndId)
  }

  override def getBranches()(implicit ec: ExecutionContext): Future[Seq[TreeBranch]] = {
    dao.getCompositeKeySet("heads").map { treesAndBranches =>
      treesAndBranches.map { case (treeId, branchId) =>
        TreeBranch(branchId, treeId)
      }.toSeq
    }
  }
}

protected case class CommitNode(
 id: String, branchId: String, previousCommitId: String, byteStore: Map[String, Array[Byte]]
 )