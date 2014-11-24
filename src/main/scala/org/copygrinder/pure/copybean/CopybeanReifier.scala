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
package org.copygrinder.pure.copybean

import org.copygrinder.pure.copybean.model._

class CopybeanReifier {

  def reify(copybean: CopybeanImpl, types: Set[CopybeanType]): ReifiedCopybean = {

    val names = types.map(cbType => {
      if (cbType.instanceNameFormat.isDefined) {
        Option((cbType.id, resolveName(cbType.instanceNameFormat.get, copybean, cbType)))
      } else {
        None
      }
    }).flatten.toMap

    new ReifiedCopybeanImpl(copybean.enforcedTypeIds, copybean.content, copybean.id, names)
  }

  protected def resolveName(format: String, copybean: CopybeanImpl, cbType: CopybeanType): String = {
    val variables = """\$(.+?)\$""".r.findAllMatchIn(format)

    variables.foldLeft(format)((result, variable) => {
      val variableString = variable.toString()
      val strippedVariable = variableString.substring(1, variableString.length - 1)

      if (strippedVariable.startsWith("content.")) {
        val valueOpt = copybean.content.fields.find(field => field._1 == strippedVariable.replace("content.", ""))
        valueOpt match {
          case Some(value) => result.replace(variableString, value._2.toString)
          case _ => result
        }
      } else if (strippedVariable == "displayName") {
        result.replace(variableString, cbType.displayName.get)
      } else {
        result
      }

    })
  }

}
