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

import org.copygrinder.pure.copybean.model.{ReifiedCopybeanImpl, ReifiedCopybean}
import org.copygrinder.pure.copybean.persistence.model.BeanDelta

import scala.collection.immutable.ListMap

class DeltaCalculator {

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

}


