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

import org.copygrinder.pure.copybean.model.CopybeanImpl

import scala.collection.immutable.ListMap

class PredefinedCopybeans {

  lazy val predefinedBeans = List(requiredValidator).map(bean => bean.id -> bean).toMap

  val requiredValidator = new CopybeanImpl("validator.required", Set("classBackedValidator"),
    ListMap(
      "displayName" -> "Required",
      "class" -> "org.copygrinder.pure.copybean.validator.RequiredValidator",
      "signature" -> ListMap(
        "fields" -> "List[String]"
      )
    )
  )


}