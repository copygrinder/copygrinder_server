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
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.{ReifiedCopybean, CopybeanType, Commit}
import org.copygrinder.pure.copybean.persistence.IdEncoderDecoder
import org.copygrinder.pure.copybean.persistence.model._
import org.mapdb.{DB, DBMaker}

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

  protected def fetchTypes(treeId: String, commitId: String)(typeIds: Set[String])
   (implicit ec: ExecutionContext): Future[Set[CopybeanType]] = {
    val typesFuture = getByIdsAndCommit(treeId, typeIds.toSeq.map(typeId => (Namespaces.cbtype, typeId)), commitId)
    typesFuture.map(obj => {
      obj.flatten.map(_.cbType).toSet
    })
  }

  def getByIdsAndCommit(treeId: String, ids: Seq[(String, String)], commitId: String)
   (implicit ec: ExecutionContext): Future[Seq[Option[PersistableObject]]] = {
    blocking {
      val root = getDb().createHashMap(treeId).makeOrGet[String, CommitNode]
      val commitNode = Option(root.get(commitId)).getOrElse(throw new CommitNotFound(commitId))

      val futures = ids.map { namespaceAndId =>
        searchByteArrays(treeId, resolveId(namespaceAndId), commitNode).flatMap(byteArrayOpt => {
          if (byteArrayOpt.isEmpty) {
            Future {
              Option.empty[PersistableObject]
            }
          } else {
            serializer.deserialize(namespaceAndId._1, fetchTypes(treeId, commitId), byteArrayOpt.get).map(Option(_))
          }
        })
      }
      Future.sequence(futures)
    }
  }

  protected def searchByteArrays(treeId: String, key: String, commitNode: CommitNode)
   (implicit ec: ExecutionContext): Future[Option[Array[Byte]]] = {

    val byteArrayOpt = commitNode.byteStore.get(key)
    if (byteArrayOpt.isEmpty && treeId != Trees.internal) {
      getBranchHeads(Trees.internal, Branches.master).map(heads => {
        val internalRoot = getDb().createHashMap(Trees.internal).makeOrGet[String, CommitNode]
        val internalCommit = internalRoot.get(heads.head.id)
        internalCommit.byteStore.get(key)
      })
    } else {
      Future {
        byteArrayOpt
      }
    }
  }

  def getHistoryByIdAndCommit(treeId: String, id: (String, String), commitId: String, limit: Int)(implicit ec: ExecutionContext): Future[Seq[Commit]] = ???

  def getBranchHeads(treeId: String, branchId: String)(implicit ec: ExecutionContext): Future[Seq[Commit]] = {
    Future {
      val heads = blocking {
        val headsMap = getDb().createHashMap("$$heads").makeOrGet[String, Set[Commit]]
        Option(headsMap.get(treeId)).getOrElse(Set())
      }
      heads.filter(_.branchId == branchId).toSeq
    }
  }

  def getCommitsByBranch(treeId: String, branchId: String, limit: Int)(implicit ec: ExecutionContext): Future[Seq[Commit]] = ???

  def query(treeId: String, commitId: String, limit: Int, query: Query)
   (implicit ec: ExecutionContext): Future[Seq[PersistableObject]] = {

    val root = getDb().createHashMap(treeId).makeOrGet[String, CommitNode]
    val commitNode = Option(root.get(commitId)).getOrElse(throw new CommitNotFound(commitId))

    val allIds = commitNode.byteStore.keys.map(splitId(_))

    val ids = if (query.namespaceRestriction.isDefined) {
      allIds.filter(_._1 == query.namespaceRestriction.get)
    } else {
      allIds
    }

    getByIdsAndCommit(treeId, ids.toSeq, commitNode.id).map(_.flatten).map(objSeq => {
      objSeq.filter(obj => {
        query.fieldsAndValues.forall { case ((namespace, fieldId), values) =>
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

    val array = fieldId.split('.')

    if (array.length > 1) {
      values.exists(value => {
        val contentOpt = reifiedCopybean.content.get(array(1))
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
    true
  }

  def commit(request: CommitRequest, datas: Seq[CommitData])
   (implicit ec: ExecutionContext): Future[Commit] = {

    val previousCommitFuture = Future {
      blocking {
        val root = getDb().createHashMap(request.treeId).makeOrGet[String, CommitNode]

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

    previousCommitFuture.flatMap { case (root, previousCommit) =>

      createNewByteStore(datas, previousCommit, request.treeId).map(newByteStore => {
        val newHash: String = buildNewHash(request, newByteStore)

        val newCommitNode = new CommitNode(newHash, request.branchId, request.parentCommitId, newByteStore)
        blocking {
          root.put(newHash, newCommitNode)
          root.getEngine.commit()
        }

        val newCommit = new Commit(newHash, request.branchId, request.parentCommitId, "")
        updateHeads(request.treeId, newCommit)

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

    datas.foldLeft(Future(previousCommit.byteStore)) { case (result, data) =>

      result.flatMap(prevByteStore => {

        if (data.obj.nonEmpty) {
          val existingBytesOpt = previousCommit.byteStore.get(resolveId(data.id))
          if (existingBytesOpt.nonEmpty) {
            serializer.deserialize(data.id._1, fetchTypes(treeId, previousCommit.id), existingBytesOpt.get).map(
              existingObj => handleUpdateCommit(data.id, prevByteStore, data.obj.get, existingObj)
            )
          } else {
            Future {
              handleNewCommit(data.id, prevByteStore, data.obj.get)
            }
          }
        } else {
          Future {
            handleDeleteCommit(data.id, prevByteStore)
          }
        }

      })
    }
  }

  protected def resolveId(id: (String, String)) = {
    id._1 + "." + id._2
  }

  protected def splitId(id: String) = {
    val array = id.split('.')
    (array(0), array(1))
  }

  protected def handleNewCommit(spaceAndId: (String, String), prevByteStore: Map[String, Array[Byte]],
   obj: PersistableObject): Map[String, Array[Byte]] = {
    val json = serializer.serialize(obj)
    prevByteStore + (resolveId(spaceAndId) -> json)
  }

  protected def handleUpdateCommit(spaceAndId: (String, String), prevByteStore: Map[String, Array[Byte]],
   obj: PersistableObject, oldObj: PersistableObject): Map[String, Array[Byte]] = {
    val json = serializer.serialize(obj)
    prevByteStore.updated(resolveId(spaceAndId), json)
  }

  protected def handleDeleteCommit(spaceAndId: (String, String), prevByteStore: Map[String, Array[Byte]]
   ): Map[String, Array[Byte]] = {
    prevByteStore - resolveId(spaceAndId)
  }

}

protected case class CommitNode(
 id: String, branchId: String, previousCommitId: String, byteStore: Map[String, Array[Byte]]
 )