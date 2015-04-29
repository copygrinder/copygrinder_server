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

import org.copygrinder.pure.copybean.model.{Copybean, CopybeanType}
import org.copygrinder.pure.copybean.persistence.model.{PersistableObject, CommitData}

class Indexer {

  def indexAddCopybean(bean: Copybean): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexUpdateCopybean(oldBean: Copybean, newBean: Copybean): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexDeleteCopybean(oldBean: Copybean): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexAddType(cbType: CopybeanType): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexUpdateType(oldType: CopybeanType, newType: CopybeanType): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexDeleteType(cbType: CopybeanType): CommitData = {
    //TODO: IMPLEMENT
    null
  }

  def indexOnly(objs: Iterable[PersistableObject]): Seq[CommitData] = {
    //TODO: IMPLEMENT
    null
  }


}
