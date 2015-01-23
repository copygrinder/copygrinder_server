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

import org.copygrinder.pure.copybean.exception.TypeValidationException
import org.copygrinder.pure.copybean.model.CopybeanFieldDef

import scala.reflect.runtime.universe._

class UntypedCaster {

  def castData[T](data: Any, field: String, fieldDef: CopybeanFieldDef)(implicit typeTag: TypeTag[T]): T = {
    val castData = doCast(data, fieldDef, field, typeTag.tpe)
    castData.asInstanceOf[T]
  }

  def castAttr[T](fieldDef: CopybeanFieldDef, attr: String)(implicit typeTag: TypeTag[T]): T = {

    val attrs = fieldDef.attributes.getOrElse(
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $attr")
    )

    val data = attrs.getOrElse(attr,
      throw new TypeValidationException(s"${fieldDef.id} requires attribute $attr")
    )

    castData(data, attr, fieldDef)(typeTag)
  }

  protected def doCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type): Any = {

    tpe.typeConstructor.toString match {
      case s if s.endsWith("Seq") => {
        handleSeqCast(data, fieldDef, parent, tpe)
      }
      case m if m.endsWith("Map") => {
        handleMapCast(data, fieldDef, parent, tpe)
      }
      case e if e.endsWith("Either") => {
        handleEitherCast(data, fieldDef, parent, tpe)
      }
      case "String" => data
      case other => throw new TypeValidationException(s"${fieldDef.id} has unknown type '$other'")
    }

  }

  protected def handleSeqCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    if (data.isInstanceOf[Seq[_]]) {
      val seq = data.asInstanceOf[Seq[Any]].zipWithIndex
      seq.map(pair => {
        val (innerData, index) = pair
        val newParent = s"$parent[$index]"
        doCast(innerData, fieldDef, newParent, tpe.typeArgs.head)
      })
    } else {
      throw new TypeValidationException(s"${fieldDef.id} requires $parent to be an array")
    }
  }

  protected def handleMapCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    if (data.isInstanceOf[Map[_, _]]) {
      val map = data.asInstanceOf[Map[Any, Any]]
      map.map(entry => {
        if (entry._1.isInstanceOf[String]) {
          entry._1 -> doCast(entry._2, fieldDef, parent + "." + entry._1, tpe.typeArgs(1))
        } else {
          throw new TypeValidationException(s"${fieldDef.id} requires $parent.${entry._1} to be an string")
        }
      })
    } else {
      throw new TypeValidationException(s"${fieldDef.id} requires $parent to be an object")
    }
  }

  protected def handleEitherCast(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type) = {
    val isLeft = checkEither(data, fieldDef, parent, tpe.typeArgs(0))
    val isRight = checkEither(data, fieldDef, parent, tpe.typeArgs(1))
    if (isLeft) {
      Left(doCast(data, fieldDef, parent, tpe.typeArgs(0)))
    } else if (isRight) {
      Right(doCast(data, fieldDef, parent, tpe.typeArgs(1)))
    } else {
      throw new TypeValidationException(
        s"${fieldDef.id} $parent is neither ${tpe.typeArgs(0)} nor ${tpe.typeArgs(1)}"
      )
    }
  }

  protected def checkEither(data: Any, fieldDef: CopybeanFieldDef, parent: String, tpe: Type): Boolean = {
    tpe.typeConstructor.toString match {
      case "String" => {
        if (data.isInstanceOf[String]) {
          true
        } else {
          false
        }
      }
      case s if s.endsWith("Seq") => {
        if (data.isInstanceOf[Seq[_]]) {
          true
        } else {
          false
        }
      }
      case other =>
        throw new TypeValidationException(
          s"${fieldDef.id} attribute $parent has an unknown Either type '$other'"
        )
    }
  }

}
