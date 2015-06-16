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
package org.copygrinder.pure.copybean.persistence

import org.copygrinder.pure.copybean.model.{CommitChange, ReifiedCopybeanImpl, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence.model.BeanDelta

import scala.collection.immutable.ListMap

class DeltaCalculator {

  protected val hashFactory = HashFactoryWrapper.hashFactory

  protected val idEncoder = new IdEncoderDecoder()

  def calcBeanDeltas(oldBeanOpt: Option[ReifiedCopybean], newBean: ReifiedCopybean): Iterable[BeanDelta] = {

    val oldBean = oldBeanOpt.getOrElse(
      ReifiedCopybeanImpl(newBean.enforcedTypeIds, ListMap(), newBean.id, newBean.types)
    )

    val oldFields = oldBean.reifiedFields.values.toSet
    val newFields = newBean.reifiedFields.values.toSet

    val changedFieldIds = newFields.diff(oldFields).map(_.fieldDef.id)

    changedFieldIds.map { fieldId =>

      val oldFieldOpt = oldBean.reifiedFields.get(fieldId)
      val newField = newBean.reifiedFields.get(fieldId).get

      BeanDelta(oldFieldOpt, oldBeanOpt, newField, newBean)
    }
  }

  def calcBeanCommitDeltas(
   oldBeans: Map[String, ReifiedCopybean], newBeans: Map[String, ReifiedCopybean]): CommitChange = {

    val results1 = oldBeans.map { case (id, oldBean) =>
      val newBeanOpt = newBeans.get(id)
      id -> calcBeanFieldDeltas(Option(oldBean), newBeanOpt)
    }

    val uniqueNewBeans = newBeans -- oldBeans.keys

    val results2 = uniqueNewBeans.map { case (id, newBean) =>
      id -> calcBeanFieldDeltas(None, Option(newBean))
    }

    CommitChange(results1 ++ results2)
  }

  protected val seed = 9283923842393L

  def calcBeanFieldDeltas(
   oldBeanOpt: Option[ReifiedCopybean], newBeanOpt: Option[ReifiedCopybean]): Map[String, String] = {

    val oldBean = oldBeanOpt.getOrElse(
      ReifiedCopybeanImpl(Set(), ListMap(), "", Set())
    )

    val newBean = newBeanOpt.getOrElse(
      ReifiedCopybeanImpl(Set(), ListMap(), "", Set())
    )

    val oldFields = oldBean.reifiedFields.values.toSet
    val newFields = newBean.reifiedFields.values.toSet

    val changedFieldIds = newFields.diff(oldFields).map(_.fieldDef.id)

    changedFieldIds.map { fieldId =>

      val bytes = newBean.reifiedFields.get(fieldId).fold(Array.empty[Byte]) { field =>
        field.value.toString.getBytes
      }

      val builder = hashFactory.newStreamingHash64(seed)
      builder.update(bytes, 0, bytes.size)
      val hash = idEncoder.encodeLong(builder.getValue)
      fieldId -> hash
    }.toMap
  }

}


