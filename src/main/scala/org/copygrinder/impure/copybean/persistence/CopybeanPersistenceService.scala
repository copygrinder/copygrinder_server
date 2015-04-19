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

import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.ReifiedField.{ListReifiedField, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.validator.FieldValidator
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CopybeanPersistenceService(
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans,
 indexer: Indexer
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  def getCommitIdOfActiveHeadOfBranch(branchId: String)(implicit siloScope: SiloScope): Future[String] = {
    //TODO: Implement real active branch head calculation
    val headsFuture = siloScope.persistor.getBranchHeads(branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      heads.headOption.getOrElse(throw new BranchNotFound(branchId))
    })

    activeHeadFuture
  }

  def fetchCopybeansFromBranchHead(id: Seq[String], branchId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    getCommitIdOfActiveHeadOfBranch(branchId).flatMap(head => {
      fetchCopybeansFromCommit(id, head)
    })
  }

  def fetchCopybeansFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    val beansFuture = fetchFromCommit(ids, commitId, "bean") { case (id, dataOpt) =>
      if (dataOpt.isEmpty) {
        predefinedCopybeans.predefinedBeans.getOrElse(id, throw new CopybeanNotFound(id))
      } else {
        val json = dataOpt.get
        parseCopybeanJson(json)
      }
    }

    beansFuture.flatMap(copybeans => {
      reifyBeans(copybeans, commitId)
    })

  }

  protected def parseCopybeanJson(json: String) = {
    implicitly[Reads[CopybeanImpl]].reads(Json.parse(json)).get
  }

  protected def reifyBeans(copybeans: Seq[Copybean], commitId: String): Future[Seq[ReifiedCopybean]] = {

    val nestedFutures = copybeans.map(copybean => {
      resolveTypes(copybean, commitId).map(types => {
        new ReifiedCopybeanImpl(copybean.enforcedTypeIds, copybean.content, copybean.id, types)
      })
    })
    Future.sequence(nestedFutures)

  }

  protected def resolveTypes(copybean: AnonymousCopybean, commitId: String)
   (implicit siloScope: SiloScope): Future[Set[CopybeanType]] = {

    fetchCopybeanTypesFromCommit(copybean.enforcedTypeIds.toSeq, commitId).map(_.toSet)

  }

  def findExpandableBeans(copybeans: Seq[ReifiedCopybean], expandableFields: Set[String], commitId: String)
   (implicit siloScope: SiloScope): Future[Map[String, ReifiedCopybean]] = {

    if (expandableFields.nonEmpty) {
      val expandAll = expandableFields.contains("*")

      val referenceFields = copybeans.flatMap(copybean => {
        copybean.fields.flatMap(field => {
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

      val beansFuture = fetchCopybeansFromCommit(referenceFields.map(_.castVal), commitId)

      beansFuture.map(beans => {
        referenceFields.map(field => {
          (field.fieldDef.id, beans.find(_.id == field.castVal).get)
        }).toMap
      })

    } else {
      Future(Map())
    }
  }

  def storeAnonBean(anonCopybean: AnonymousCopybean, commit: NewCommit)
   (implicit siloScope: SiloScope): Future[String] = {

    val id = idEncoderDecoder.encodeUuid(UUID.randomUUID())
    val copybean = new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content)
    storeBean(copybean, commit)
  }

  protected def storeBean(rawCopybean: Copybean, commit: NewCommit)(implicit siloScope: SiloScope): Future[String] = {

    val copybeanFuture = reifyBeans(Seq(rawCopybean), commit.parentCommitId)

    copybeanFuture.flatMap(copybeans => {
      val copybean = copybeans.head
      enforceTypes(copybean, commit.parentCommitId).flatMap(_ => {
        val index = indexer.indexCopybeans(Seq(copybean))
        val json = Json.stringify(unreifiedCopybeanWrites.writes(copybean))
        siloScope.persistor.commit(commit, Map(("bean", copybean.id) -> Some(json)), index)
      })
    })

  }


  def findAllByCommit(commitId: String)(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    logger.debug("Finding all copybeans")
    siloScope.persistor.findAll(commitId, 100, "bean").flatMap(beanStrings => {
      val copybeans = beanStrings.map(parseCopybeanJson(_))
      reifyBeans(copybeans, commitId)
    })
  }

  def findByCommit(commitId: String, params: Seq[(String, String)])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    if (params.nonEmpty) {
      val query = new Query(params) //TODO: Implement a proper query builder.
      siloScope.persistor.query(commitId, 100, "bean", query).flatMap(beanStrings => {
        val copybeans = beanStrings.map(parseCopybeanJson(_))
        reifyBeans(copybeans, commitId)
      })
    } else {
      findAllByCommit(commitId)
    }
  }


  def update(id: String, anonCopybean: AnonymousCopybean, commit: NewCommit)
   (implicit siloScope: SiloScope): Future[String] = {

    val existingBeanFuture = siloScope.persistor.getByIdsAndCommit("bean", Seq(id), commit.parentCommitId)

    existingBeanFuture.map(seqOpt => {
      if (seqOpt.isEmpty || seqOpt.head.isEmpty) {
        throw new CopybeanNotFound(id)
      }
    }).flatMap { _ =>

      val rawBean = Seq(new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content))

      val copybeanFuture = reifyBeans(rawBean, commit.parentCommitId)

      copybeanFuture.flatMap(copybeans => {

        val copybean = copybeans.head

        enforceTypes(copybean, commit.parentCommitId).flatMap(_ => {

          val index = indexer.indexCopybeans(Seq(copybean))

          val json = Json.stringify(unreifiedCopybeanWrites.writes(copybean))
          val data = Map(("bean", id) -> Some(json))

          siloScope.persistor.commit(commit, data, index)

        })

      })
    }

  }

  def delete(id: String, commit: NewCommit)(implicit siloScope: SiloScope): Future[String] = {

    val key = ("bean", id)

    val data = Map(key -> None)

    val index = new IndexData(Map(key -> Map()))

    siloScope.persistor.commit(commit, data, index)
  }

  def createSilo()(implicit siloScope: SiloScope): Future[Unit] = {
    siloScope.persistor.initSilo().flatMap(_ => {
      val beansIndex = indexer.indexCopybeans(predefinedCopybeans.predefinedBeans.values)
      siloScope.persistor.addToIndex(beansIndex).flatMap(_ => {
        val typesIndex = indexer.indexCopybeanTypes(predefinedCopybeanTypes.predefinedTypes.values)
        siloScope.persistor.addToIndex(typesIndex)
      })
    })
  }

  //TODO: TYPE ENFORCER SHOULD TAKE A REIFIED BEAN
  protected def enforceTypes(copybean: ReifiedCopybean, commitId: String)
   (implicit siloScope: SiloScope): Future[Unit] = {

    val future = copybean.types.map { copybeanType =>
      fetchValidators(copybeanType, commitId).map(validatorBeansMap => {

        val validatorInstances = fetchClassBackedValidators(validatorBeansMap.values)
        (validatorBeansMap, validatorInstances)

      })
    }

    val futureSeq = Future.sequence(future)

    futureSeq.flatMap(typesAndValidatorMaps => {

      val validatorBeansMap = typesAndValidatorMaps.flatMap(_._1).toMap
      val validatorInstances = typesAndValidatorMaps.flatMap(_._2).toMap

      val refs = copybeanTypeEnforcer.enforceTypes(copybean, validatorBeansMap, validatorInstances)
      checkRefs(refs, commitId)
    })

  }

  protected def fetchClassBackedValidators(validators: Iterable[Copybean]) = {

    validators.foldLeft(Map[String, FieldValidator]()) { (result, validator) =>

      if (validator.enforcedTypeIds.contains("classBackedFieldValidator")) {

        val className = validator.content.getOrElse("class",
          throw new TypeValidationException(s"Couldn't find a class for validator '${validator.id}'")
        )

        className match {
          case classNameString: String =>
            try {
              result + (classNameString -> Class.forName(classNameString).newInstance().asInstanceOf[FieldValidator])
            } catch {
              case e: ClassNotFoundException =>
                throw new TypeValidationException(
                  s"Couldn't find class '$classNameString' for validator '${validator.id}'"
                )
            }
          case x => throw new TypeValidationException(
            s"Validator '${validator.id}' did not specify class as a String but the value '$x' which is a ${x.getClass}"
          )
        }
      } else {
        result
      }
    }

  }

  protected def fetchValidators(copybeanType: CopybeanType, commitId: String)
   (implicit siloScope: SiloScope): Future[Map[String, ReifiedCopybean]] = {

    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.foldLeft(Future(Map[String, ReifiedCopybean]())) { (resultFuture, field) =>

        if (field.validators.isDefined) {
          val validatorTypes = field.validators.get.map(_.`type`)

          val qualifiedValidatorIds = validatorTypes.map(typeId => s"validator.$typeId")
          val validatorsFuture = fetchCopybeansFromCommit(qualifiedValidatorIds, commitId)

          validatorsFuture.flatMap(validatorBeans =>
            resultFuture.map(result => {
              result ++ validatorBeans.map(validator => validator.id -> validator).toMap
            })
          )
        } else {
          resultFuture
        }
      }

    } else {
      Future(Map.empty)
    }
  }

  protected def checkRefs(refs: Map[String, CopybeanFieldDef], commitId: String)
   (implicit siloScope: SiloScope): Future[Unit] = {
    if (refs.nonEmpty) {
      val sourceIds = refs.keySet
      val foundIdsFuture = siloScope.persistor.getByIdsAndCommit("bean", sourceIds.toSeq, commitId)

      foundIdsFuture.flatMap(foundIds => {

        val flattenedFoundIds = foundIds.flatten

        val diffs = sourceIds.diff(flattenedFoundIds.toSet)
        if (diffs.nonEmpty) {
          throw new TypeValidationException(s"Reference(s) made to non-existent bean(s): " + diffs.mkString)
        }

        fetchCopybeansFromCommit(flattenedFoundIds, commitId).map(copybeans => {

          copybeans.foreach(bean => {
            val refField = refs.get(bean.id).get.asInstanceOf[ReferenceType]
            val validType = refField.refs.exists(ref => {
              ref.validationTypes.forall(refTypeId => {
                bean.enforcedTypeIds.contains(refTypeId)
              })
            })
            if (!validType) {
              throw new TypeValidationException("Reference made to a type not contained within refValidationTypes: " +
               bean.id + " is a " + bean.enforcedTypeIds.mkString(",") + " which is not in "
               + refField.refs.flatMap(_.validationTypes).mkString(","))
            }
          })

        })

      })

    } else {
      Future(Unit)
    }
  }

}
