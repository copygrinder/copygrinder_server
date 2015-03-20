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

import org.apache.commons.io.FileUtils
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
 hashedFileResolver: HashedFileResolver,
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 copybeanReifier: CopybeanReifier,
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  protected def fetchCopybean(id: String)(implicit siloScope: SiloScope): ReifiedCopybean = {
    checkSiloExists()
    val file = hashedFileResolver.locate(id, "json", siloScope.beanDir)

    val copybean = if (!file.exists()) {
      predefinedCopybeans.predefinedBeans.getOrElse(id, throw new CopybeanTypeNotFound(id))
    } else {
      val json = FileUtils.readFileToByteArray(file)
      implicitly[Reads[CopybeanImpl]].reads(Json.parse(json)).get
    }

    val types: Set[CopybeanType] = resolveTypes(copybean)
    copybeanReifier.decorate(copybean, types)
  }

  protected def expandRefs(copybean: ReifiedCopybean, expandableBeans: Map[String, ReifiedCopybean])
   (implicit siloScope: SiloScope) = {

  }

  def findExpandableBeans(copybeans: Seq[ReifiedCopybean], expandableFields: Set[String])
   (implicit siloScope: SiloScope): Map[String, ReifiedCopybean] = {

    if (expandableFields.nonEmpty) {
      val expandAll = expandableFields.contains("*")

      val referenceFields = copybeans.flatMap(copybean => {
        copybean.fields.flatMap(field => {
          if (expandAll || expandableFields.contains("content." + field._1)) {
            field._2 match {
              case r: ReferenceReifiedField => Seq(Some(r))
              case l: ListReifiedField => l.castVal.map(field => {
                if (field.isInstanceOf[ReferenceReifiedField]) {
                  Some(field.asInstanceOf[ReferenceReifiedField])
                } else {
                  None
                }
              })
              case _ => Seq(None)
            }
          } else {
            Seq(None)
          }
        }).flatten.toSet
      })

      val future = fetchCopybeans(referenceFields.map(_.castVal).toSeq)
      val beans = Await.result(future, 5 seconds)

      referenceFields.map(field => {
        (field.fieldDef.id, beans.find(_.id == field.castVal).get)
      }).toMap
    } else {
      Map()
    }
  }

  protected def resolveTypes(copybean: AnonymousCopybean)(implicit siloScope: SiloScope): Set[CopybeanType] = {
    val future = copybean.enforcedTypeIds.map { typeId =>
      cachedFetchCopybeanType(typeId)
    }
    val futureSeq = Future.sequence(future)
    val types = Await.result(futureSeq, 5 seconds)
    types
  }

  def cachedFetchCopybean(id: String)
   (implicit siloScope: SiloScope): Future[ReifiedCopybean] = siloScope.beanCache(id) {
    fetchCopybean(id)
  }

  def store(anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): Copybean = {
    val id = idEncoderDecoder.encodeUuid(UUID.randomUUID())
    val copybean = new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content)
    store(copybean)
    copybean
  }

  def store(rawCopybean: Copybean)(implicit siloScope: SiloScope): String = {

    checkSiloExists()

    val copybean = copybeanReifier.unreify[Copybean](rawCopybean, resolveTypes(rawCopybean))

    enforceTypes(copybean)

    val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
    val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
    siloScope.beanGitRepo.add(file, json)
    siloScope.indexer.addCopybean(copybean)

    copybean.id
  }

  def find()(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    checkSiloExists()
    logger.debug("Finding all copybeans")
    val copybeanIds = siloScope.indexer.findCopybeanIds()
    fetchCopybeans(copybeanIds)
  }

  def find(params: Seq[(String, String)])(implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    if (params.nonEmpty) {
      checkSiloExists()
      val copybeanIds = siloScope.indexer.findCopybeanIds(params)
      fetchCopybeans(copybeanIds)
    } else {
      find()
    }
  }

  protected def fetchCopybeans(copybeanIds: Seq[String])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    val futures = copybeanIds.map(id => {
      cachedFetchCopybean(id)
    })
    Future.sequence(futures)
  }

  def update(id: String, anonCopybean: AnonymousCopybean)(implicit siloScope: SiloScope): Unit = {

    checkSiloExists()

    val rawCopybean = new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content)

    val copybean = copybeanReifier.unreify[Copybean](rawCopybean, resolveTypes(rawCopybean))

    enforceTypes(copybean)

    val file = hashedFileResolver.locate(copybean.id, "json", siloScope.beanDir)
    val json = Json.stringify(implicitly[Writes[Copybean]].writes(copybean))
    if (!file.exists()) {
      throw new CopybeanNotFound(id)
    }
    siloScope.beanGitRepo.update(file, json)
    siloScope.beanCache.remove(id)

    siloScope.indexer.updateCopybean(copybean)
  }

  protected def enforceTypes(copybean: Copybean)(implicit siloScope: SiloScope, ec: ExecutionContext): Unit = {
    val future = copybean.enforcedTypeIds.map { typeId =>
      cachedFetchCopybeanType(typeId).map { copybeanType =>
        val validatorBeansMap = fetchValidators(copybeanType)
        val validatorInstances = fetchClassBackedValidators(validatorBeansMap.map(_._2))
        (copybeanType, validatorBeansMap, validatorInstances)
      }
    }
    val futureSeq = Future.sequence(future)
    val typesAndValidatorMaps = Await.result(futureSeq, 5 seconds)

    val copybeanTypes = typesAndValidatorMaps.map(_._1)
    val validatorBeansMap = typesAndValidatorMaps.flatMap(_._2).toMap
    val validatorInstances = typesAndValidatorMaps.flatMap(_._3).toMap

    val refs = copybeanTypeEnforcer.enforceTypes(copybeanTypes, copybean, validatorBeansMap, validatorInstances)
    checkRefs(refs)

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
        refField.refs.exists(ref => {
          ref.validationTypes.forall(refTypeId => {
            bean.enforcedTypeIds.contains(refTypeId)
          })
        })
      })
    }
  }


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
