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
import org.copygrinder.pure.copybean.CopybeanReifier
import org.copygrinder.pure.copybean.exception._
import org.copygrinder.pure.copybean.model.ReifiedField.{ListReifiedField, ReferenceReifiedField}
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence._
import org.copygrinder.pure.copybean.validator.FieldValidator
import play.api.libs.json._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class CopybeanPersistenceService(
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 copybeanReifier: CopybeanReifier,
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  protected def getCommitIdOfActiveHeadOfBranch(branchId: String)(implicit siloScope: SiloScope): Future[String] = {
    //TODO: Implement real active branch head calculation
    val headsFuture = siloScope.persistor.getBranchHeads(branchId)

    val activeHeadFuture = headsFuture.map(heads => {
      val activeHead = heads.headOption.getOrElse(throw new BranchNotFound(branchId))
      activeHead
    })

    activeHeadFuture
  }

  protected def fetchCopybeansFromBranchHead(id: Seq[String], branchId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    getCommitIdOfActiveHeadOfBranch(branchId).flatMap(head => {
      fetchCopybeansFromCommit(id, head)
    })

  }

  protected def fetchCopybeansFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    checkSiloExists()

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
        copybeanReifier.decorate(copybean, types)
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

  def storeBean(rawCopybean: Copybean, commit: NewCommit)(implicit siloScope: SiloScope): Future[String] = {

    checkSiloExists()

    resolveTypes(rawCopybean, commit.parentCommitId).map(types => {
      val copybean = copybeanReifier.unreify[Copybean](rawCopybean, types)

      val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
      siloScope.persistor.commit(commit, Map(("bean", copybean.id) -> json))

      copybean.id
    })

  }


  def findAll(branchId: String)(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    checkSiloExists()
    logger.debug("Finding all copybeans")
    val headCommitFuture = getCommitIdOfActiveHeadOfBranch(branchId)
    headCommitFuture.flatMap(headCommit => {
      siloScope.persistor.findAll(headCommit, 100, "bean").flatMap(beanStrings => {
        val copybeans = beanStrings.map(parseCopybeanJson(_))
        reifyBeans(copybeans, headCommit)
      })
    })
  }

  def find(branchId: String, params: Seq[(String, String)])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    if (params.nonEmpty) {
      checkSiloExists()
      val headCommitFuture = getCommitIdOfActiveHeadOfBranch(branchId)
      headCommitFuture.flatMap(headCommit => {
        val query = new Query(params) //TODO: Implement a proper query builder.
        siloScope.persistor.query(headCommit, 100, "bean", query).flatMap(beanStrings => {
          val copybeans = beanStrings.map(parseCopybeanJson(_))
          reifyBeans(copybeans, headCommit)
        })
      })
    } else {
      findAll(branchId)
    }
  }


  def update(id: String, anonCopybean: AnonymousCopybean, commit: NewCommit)
   (implicit siloScope: SiloScope): Future[String] = {

    checkSiloExists()

    val existingBeanFuture = siloScope.persistor.getByIdsAndCommit("bean", Seq(id), commit.parentCommitId)

    existingBeanFuture.map(opt => {
      if (opt.isEmpty) {
        throw new CopybeanNotFound(id)
      }
    }).flatMap { _ =>

      val rawCopybean = new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content)

      resolveTypes(rawCopybean, commit.parentCommitId).flatMap(types => {

        val copybean = copybeanReifier.unreify[Copybean](rawCopybean, types)

        val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
        val data = Map(("bean", id) -> json)

        siloScope.persistor.commit(commit, data)
      })
    }

  }

  //TODO: TYPE ENFORCER SHOULD TAKE A REIFIED BEAN
/*  protected def enforceTypes(copybean: Copybean, commitId: String)
   (implicit siloScope: SiloScope): Unit = {

    val future = copybean.enforcedTypeIds.map { typeId =>
      fetchCopybeanTypesFromCommit(Seq(typeId), commitId).map { copybeanTypes =>
        val copybeanType = copybeanTypes.head
        val validatorBeansMap = fetchValidators(copybeanType)
        val validatorInstances = fetchClassBackedValidators(validatorBeansMap.map(_._2))
        (copybeanType, validatorBeansMap, validatorInstances)
      }
    }
    val futureSeq = Future.sequence(future)

    futureSeq.map(typesAndValidatorMaps => {

      val copybeanTypes = typesAndValidatorMaps.map(_._1)
      val validatorBeansMap = typesAndValidatorMaps.flatMap(_._2).toMap
      val validatorInstances = typesAndValidatorMaps.flatMap(_._3).toMap

      val refs = copybeanTypeEnforcer.enforceTypes(copybeanTypes, copybean, validatorBeansMap, validatorInstances)
      checkRefs(refs)
    })

  }

  protected def fetchClassBackedValidators(validators: Iterable[Copybean]) = {

    validators.foldLeft(Map[String, FieldValidator]()) { (result, validator) =>

      if (validator.enforcedTypeIds.contains("classBackedFieldValidator")) {

        val className = validator.content.getOrElse("class",
          throw new TypeValidationException(s"Couldn't find a class for validator '${validator.id}'")
        )

        className match {
          case classNameString: String => {
            try {
              result + (classNameString -> Class.forName(classNameString).newInstance().asInstanceOf[FieldValidator])
            } catch {
              case e: ClassNotFoundException =>
                throw new TypeValidationException(
                  s"Couldn't find class '$classNameString' for validator '${validator.id}'"
                )
            }
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

  protected def fetchValidators(copybeanType: CopybeanType)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Map[String, ReifiedCopybean] = {
    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.foldLeft(Map[String, ReifiedCopybean]()) { (result, field) =>
        if (field.validators.isDefined) {
          val validatorTypes = field.validators.get.map(_.`type`)
          val validatorBeansFuture = Future.sequence(
            validatorTypes.map(typeId => cachedFetchCopybean(s"validator.$typeId"))
          )
          val validatorBeans = Await.result(validatorBeansFuture, 5 seconds)
          result ++ validatorBeans.map(validator => validator.id -> validator).toMap
        } else {
          result
        }
      }
    } else {
      Map.empty
    }
  }

  protected def checkRefs(refs: Map[String, CopybeanFieldDef])(implicit siloScope: SiloScope, ec: ExecutionContext) = {
    if (refs.nonEmpty) {
      val params = refs.toSeq.map(ref => ("id~", ref._1))
      val ids = siloScope.indexer.findCopybeanIds(params).toSet
      val diffs = refs.map(_._1).toSet.diff(ids)
      if (diffs.nonEmpty) {
        throw new TypeValidationException(s"Reference(s) made to non-existent bean(s): " + diffs.mkString)
      }
      val copybeans = Await.result(fetchCopybeans(ids.toSeq), 5 seconds)
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
           + refField.refs.map(_.validationTypes).flatten.mkString(","))
        }
      })
    }
  }*/


  def delete(id: String)(implicit siloScope: SiloScope): Unit = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)
    siloScope.beanGitRepo.delete(file)
    siloScope.indexer.deleteCopybean(id)
    siloScope.beanCache.remove(id)
  }

  def createSilo()(implicit siloScope: SiloScope): Unit = {
    if (siloScope.indexDir.exists) {
      throw new SiloAlreadyInitialized(siloScope.siloId)
    }
    siloScope.indexRebuilder.rebuild()
  }


}
