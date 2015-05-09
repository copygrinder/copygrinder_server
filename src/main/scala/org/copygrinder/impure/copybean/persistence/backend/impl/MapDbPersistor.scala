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
import java.util.concurrent.atomic.AtomicReference

import net.jpountz.xxhash.XXHashFactory
import org.apache.commons.io.FileUtils
import org.copygrinder.impure.copybean.persistence.backend.{PersistentObjectSerializer, VersionedDataPersistor}
import org.copygrinder.pure.collections.IndexedHashMap
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{Commit, CopybeanType, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder
import org.copygrinder.pure.copybean.persistence.model._
import org.mapdb.{DB, DBMaker}

import scala.annotation.tailrec
import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContext, Future, blocking}

//TODO: IMPLEMENT
class MapDbPersistor(silo: String, storageDir: File, serializer: PersistentObjectSerializer[Array[Byte]])
 extends VersionedDataPersistor {

  protected val db = new AtomicReference[Option[DB]](None)

  protected val hashFactory = XXHashFactory.fastestInstance()

  protected val idEncoder = new IdEncoderDecoder()

  protected def getDb(allowNew: Boolean = false) = {
    if (db.get().isEmpty) {

      if (!allowNew && (!storageDir.exists() || storageDir.list().isEmpty)) {
        throw new SiloNotInitialized(silo)
      }

      FileUtils.forceMkdir(storageDir)
      val newDb = DBMaker
       .newFileDB(new File(storageDir, "datastore.mapdb"))
       .closeOnJvmShutdown()
       .cacheLRUEnable()
       .make()

      val success = db.compareAndSet(None, Some(newDb))
      if (success) {
        newDb
      } else {
        newDb.close()
        db.get().get
      }
    } else {
      db.get().get
    }
  }

  def initSilo()(implicit ec: ExecutionContext): Future[Unit] = {
    Future {
      blocking {
        if (storageDir.exists() && storageDir.list().nonEmpty) {
          throw new SiloAlreadyInitialized(silo)
        }
        getDb(allowNew = true)
      }
    }
  }

  protected def fetchTypesFromCommitNodes(commitNodes: Seq[CommitNode])(typeIds: Set[String])
   (implicit ec: ExecutionContext): Future[Set[CopybeanType]] = {
    val typesFuture = getByIdsAndCommitNodes(typeIds.toSeq.map(typeId => (Namespaces.cbtype, typeId)), commitNodes)
    typesFuture.map(obj => {
      obj.flatten.map(_.cbType).toSet
    })
  }

  protected def getCommits(commitIds: Seq[CommitId]): Seq[CommitNode] = {
    blocking {
      commitIds.flatMap { commitId =>
        val root = getDb().createHashMap(commitId.treeId).makeOrGet[String, CommitNode]
        val commitNodeOpt = Option(root.get(commitId.id))
        if (commitNodeOpt.isDefined) {
          commitNodeOpt
        } else if (commitId.id.isEmpty) {
          None
        } else {
          throw new CommitNotFound(commitId)
        }
      }
    }
  }


  def getByIdsAndCommits(ids: Seq[(String, String)], commitIds: Seq[CommitId])
   (implicit ec: ExecutionContext): Future[Seq[Option[PersistableObject]]] = {
    val commits = getCommits(commitIds)
    getByIdsAndCommitNodes(ids, commits)
  }

  protected def getByIdsAndCommitNodes(ids: Seq[(String, String)], commits: Seq[CommitNode])
   (implicit ec: ExecutionContext): Future[Seq[Option[PersistableObject]]] = {

    val futures = ids.map {
      namespaceAndId =>

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

    Future.sequence(futures)
  }

  def getHistoryByIdAndCommits(id: (String, String), commitIds: Seq[CommitId], limit: Int)
   (implicit ec: ExecutionContext): Future[Seq[Commit]] = ???

  def getBranchHeads(branchId: BranchId)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    Future {
      val heads = blocking {
        val headsMap = getDb().createHashMap("$$heads").makeOrGet[String, Set[Commit]]
        Option(headsMap.get(branchId.treeId)).getOrElse(throw new TreeNotFound(branchId.treeId))
      }
      heads.filter(_.branchId == branchId.id).toSeq
    }
  }

  def getCommitsByBranch(branchId: BranchId, limit: Int)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    getBranchHeads(branchId).map {
      commits =>
        val commitIds = commits.map(c => CommitId(c.id, branchId.treeId))
        getPreviousCommits(IndexedHashMap[String, Commit](), commitIds, branchId, limit)
    }
  }

  @tailrec
  protected final def getPreviousCommits(
   results: IndexedHashMap[String, Commit], commits: Seq[CommitId], branchId: BranchId, limit: Int
   ): Seq[Commit] = {

    val commitNodes = getCommits(commits)
    val castCommits = commitNodes.map(commitNodeToCommit)
    val newResults = results ++ castCommits.map(c => c.id -> c)

    val previousCommitIds = commitNodes
     .filterNot(c => newResults.contains(c.previousCommitId))
     .map(c => CommitId(c.previousCommitId, branchId.treeId))
     .distinct

    if (newResults.size >= limit || previousCommitIds.isEmpty) {
      newResults.values.take(limit).toSeq
    } else {
      getPreviousCommits(newResults, previousCommitIds, branchId, limit)
    }
  }

  protected def commitNodeToCommit(node: CommitNode): Commit = {
    Commit(node.id, node.branchId, node.previousCommitId, "")
  }

  def query(commitIds: Seq[CommitId], limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[PersistableObject]] = {

    val commits = getCommits(commitIds)

    val ids = commits.flatMap {
      commitNode =>

        val allIds = commitNode.byteStore.keys.map(splitId(_))

        if (query.namespaceRestriction.isDefined) {
          allIds.filter(_._1 == query.namespaceRestriction.get)
        } else {
          allIds
        }
    }

    doQuery(commits, limit, ids, query)
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

    val previousCommitFuture = Future {
      blocking {
        val root = getDb().createHashMap(request.branchId.treeId).makeOrGet[String, CommitNode]

        val previousCommitOpt = Option(root.get(request.parentCommitId))
        val previousCommit = if (previousCommitOpt.isEmpty) {
          if (request.parentCommitId.isEmpty) {
            new CommitNode("", "", "", Map())
          } else {
            throw new BadParent("Parent Commit doesn't exist: " + request.parentCommitId)
          }
        } else {
          previousCommitOpt.get
        }

        (root, previousCommit)
      }
    }

    previousCommitFuture.flatMap {
      case (root, previousCommit) =>

        createNewByteStore(datas, previousCommit, request.branchId.treeId).map(newByteStore => {
          val newHash: String = buildNewHash(request, newByteStore)

          val newCommitNode = new CommitNode(newHash, request.branchId.id, request.parentCommitId, newByteStore)
          blocking {
            root.put(newHash, newCommitNode)
            root.getEngine.commit()
          }

          val newCommit = new Commit(newHash, request.branchId.id, request.parentCommitId, "")
          updateHeads(request.branchId.treeId, newCommit)

          newCommit
        })
    }
  }

  protected def updateHeads(treeId: String, newCommit: Commit) = {

    val headsMap = getDb().createHashMap("$$heads").makeOrGet[String, Set[Commit]]
    val existingHeads = Option(headsMap.get(treeId)).getOrElse(Set())

    val newHeads = existingHeads.filter(_.id != newCommit.parentCommitId) + newCommit
    blocking {
      headsMap.put(treeId, newHeads)
      headsMap.getEngine.commit()
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

  override def getBranches()(implicit ec: ExecutionContext): Future[Seq[BranchId]] = {
    Future {
      blocking {

        val headsMap = getDb().createHashMap("$$heads").makeOrGet[String, Set[Commit]]

        headsMap.entrySet().flatMap {
          entry =>
            val treeId = entry.getKey
            entry.getValue.map {
              commit =>
                BranchId(commit.branchId, treeId)
            }
        }.toSeq
      }
    }
  }
}

protected case class CommitNode(
 id: String, branchId: String, previousCommitId: String, byteStore: Map[String, Array[Byte]]
 )