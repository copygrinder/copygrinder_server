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

import org.copygrinder.impure.system.SiloScope
import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.model._
import org.copygrinder.pure.copybean.persistence.CopybeanTypeEnforcer
import org.copygrinder.pure.copybean.persistence.model.TreeCommit
import org.copygrinder.pure.copybean.validator.FieldValidator

import scala.concurrent.{ExecutionContext, Future}

class TypeEnforcer(copybeanTypeEnforcer: CopybeanTypeEnforcer) {

  type AddInternalCommit = (Seq[TreeCommit]) => Future[Seq[TreeCommit]]

  type FetchCopybeansFromCommits = (Seq[String], Seq[TreeCommit]) => Future[Seq[ReifiedCopybean]]

  def enforceTypes(copybean: ReifiedCopybean, commitIds: Seq[TreeCommit])
   (addInternalCommit: AddInternalCommit, fetchCopybeansFromCommits: FetchCopybeansFromCommits)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Unit] = {

    val commitsFuture = addInternalCommit(commitIds)

    commitsFuture.flatMap { commitIdsWithInternal =>

      val future = copybean.types.map { copybeanType =>
        fetchValidators(copybeanType, commitIdsWithInternal)(fetchCopybeansFromCommits).map(validatorBeansMap => {

          val validatorInstances = fetchClassBackedValidators(validatorBeansMap.values)
          (validatorBeansMap, validatorInstances)

        })
      }

      val futureSeq = Future.sequence(future)

      futureSeq.flatMap(typesAndValidatorMaps => {

        val validatorBeansMap = typesAndValidatorMaps.flatMap(_._1).toMap
        val validatorInstances = typesAndValidatorMaps.flatMap(_._2).toMap

        val refs = copybeanTypeEnforcer.enforceTypes(copybean, validatorBeansMap, validatorInstances)
        checkRefs(refs, commitIdsWithInternal)(fetchCopybeansFromCommits)
      })
    }

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

  protected def fetchValidators(copybeanType: CopybeanType, commitIds: Seq[TreeCommit])
   (fetchCopybeansFromCommits: FetchCopybeansFromCommits)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Map[String, ReifiedCopybean]] = {

    if (copybeanType.fields.isDefined) {
      copybeanType.fields.get.foldLeft(Future(Map[String, ReifiedCopybean]())) { (resultFuture, field) =>

        if (field.validators.isDefined) {
          val validatorTypes = field.validators.get.map(_.`type`)

          val qualifiedValidatorIds = validatorTypes.map(typeId => s"validator.$typeId")
          val validatorsFuture = fetchCopybeansFromCommits(qualifiedValidatorIds, commitIds)

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

  protected def checkRefs(refs: Map[String, CopybeanFieldDef], commitIds: Seq[TreeCommit])
   (fetchCopybeansFromCommits: FetchCopybeansFromCommits)
   (implicit siloScope: SiloScope, ec: ExecutionContext): Future[Unit] = {
    if (refs.nonEmpty) {
      val sourceIds = refs.keySet
      val foundIdsFuture = siloScope.persistor.getByIdsAndCommits(
        sourceIds.toSeq, commitIds
      )

      foundIdsFuture.flatMap(foundIds => {

        val flattenedFoundIds = foundIds.flatten.map(_._1.id)

        val diffs = sourceIds.diff(flattenedFoundIds.toSet)
        if (diffs.nonEmpty) {
          throw new TypeValidationException(s"Reference(s) made to non-existent bean(s): " + diffs.mkString)
        }

        fetchCopybeansFromCommits(flattenedFoundIds, commitIds).map(copybeans => {

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
