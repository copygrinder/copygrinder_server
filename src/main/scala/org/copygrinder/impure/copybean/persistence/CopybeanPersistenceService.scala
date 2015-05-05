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
import org.copygrinder.pure.copybean.persistence.model._
import org.copygrinder.pure.copybean.validator.FieldValidator

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CopybeanPersistenceService(
 copybeanTypeEnforcer: CopybeanTypeEnforcer,
 idEncoderDecoder: IdEncoderDecoder,
 _predefinedCopybeanTypes: PredefinedCopybeanTypes,
 predefinedCopybeans: PredefinedCopybeans
 ) extends PersistenceSupport {

  override protected var predefinedCopybeanTypes = _predefinedCopybeanTypes

  def fetchCopybeansFromCommit(ids: Seq[String], commitId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    fetchFromCommit(ids.map(id => (Namespaces.bean, id)), commitId) {
      case ((namespace, id), dataOpt) =>
        if (dataOpt.isEmpty) {
          throw new CopybeanNotFound(id)
        } else {
          dataOpt.get.bean
        }
    }
  }

  protected def reifyBeans(copybeans: Seq[Copybean], commitId: String)
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {

    val nestedFutures = copybeans.map(copybean => {
      resolveTypes(copybean, commitId).map(types => {
        ReifiedCopybean(copybean, types)
      })
    })
    Future.sequence(nestedFutures)
  }

  protected def resolveTypes(copybean: AnonymousCopybean, commitId: String)
   (implicit siloScope: SiloScope): Future[Set[CopybeanType]] = {

    fetchCopybeanTypesFromCommit(copybean.enforcedTypeIds.toSeq, commitId).map(_.toSet)

  }

  def findExpandableBeans(copybeans: Seq[ReifiedCopybean], expandableFields: List[String], commitId: String)
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

    val newCopybeanFuture = reifyBeans(rawCopybeans, commit.parentCommitId)

    newCopybeanFuture.flatMap(copybeans => {
      val beanAndDataFutures = copybeans.map(newBean => {
        enforceTypes(newBean, commit.parentCommitId).map(_ => {
          (newBean, CommitData((Namespaces.bean, newBean.id), Some(PersistableObject(newBean))))
        })
      })
      Future.sequence(beanAndDataFutures).flatMap(beanAndDataSeq => {
        val commitFuture = siloScope.persistor.commit(commit, beanAndDataSeq.map(_._2))
        commitFuture.map(commit => (commit.id, beanAndDataSeq.map(_._1)))
      })
    })
  }

  def findByCommit(commitId: String, params: Map[String, List[String]])
   (implicit siloScope: SiloScope): Future[Seq[ReifiedCopybean]] = {
    val query = new Query(params.map(v => (Namespaces.bean, v._1) -> v._2), Some(Namespaces.bean))
    siloScope.persistor.query(Trees.userdata, commitId, siloScope.defaultLimit, query).map(objects => {
      objects.map(_.bean)
    })
  }


  def update(id: String, anonCopybean: AnonymousCopybean, commit: CommitRequest)
   (implicit siloScope: SiloScope): Future[String] = {

    val rawBean = Seq(new CopybeanImpl(id, anonCopybean.enforcedTypeIds, anonCopybean.content))

    val copybeanFuture = reifyBeans(rawBean, commit.parentCommitId)

    copybeanFuture.flatMap(copybeans => {

      val copybean = copybeans.head

      enforceTypes(copybean, commit.parentCommitId).flatMap(_ => {
        val data = CommitData((Namespaces.bean, copybean.id), Some(PersistableObject(copybean)))
        siloScope.persistor.commit(commit, Seq(data)).map(_.id)
      })

    })
  }

  def delete(id: String, commit: CommitRequest)(implicit siloScope: SiloScope): Future[String] = {
    val data = CommitData((Namespaces.bean, id), None)
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
        new CommitData((Namespaces.bean, bean.id), Some(PersistableObject(bean)))
      }.toSeq

      val types = predefinedCopybeanTypes.predefinedTypes.values.map { cbType =>
        new CommitData((Namespaces.cbtype, cbType.id), Some(PersistableObject(cbType)))
      }

      val commit = new CommitRequest(Trees.internal, Branches.master, "", "", "")
      siloScope.persistor.commit(commit, beanObjs ++ types)
    })
  }

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
      val foundIdsFuture = siloScope.persistor.getByIdsAndCommit(
        Trees.userdata, sourceIds.toSeq.map(v => ("bean", v)), commitId
      )

      foundIdsFuture.flatMap(foundIds => {

        val flattenedFoundIds = foundIds.flatten.map(_.bean.id)

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
