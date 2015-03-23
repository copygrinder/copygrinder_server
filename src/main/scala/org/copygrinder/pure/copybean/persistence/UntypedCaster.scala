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


class UntypedCaster {

  def optToMap(data: Option[Any], parentId: String, targetId: String): Map[Any, Any] = {
    if (data.isDefined) {
      if (data.get.isInstanceOf[Map[_, _]]) {
        data.get.asInstanceOf[Map[Any, Any]]
      } else {
        throw new TypeValidationException(s"$parentId requires $targetId to be a Map, not $data")
      }
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId")
    }
  }

  def optToMapThen[T](data: Option[Any], parentId: String, targetId: String)
   (func: (Map[Any, Any], String, String) => T): T = {
    val map = optToMap(data, parentId, targetId)
    func(map, parentId, targetId)
  }

  def mapGetToSeq(data: Map[Any, Any], key: Any, parentId: String, targetId: String): Seq[Any] = {

    val valueOpt = data.get(key)
    if (valueOpt.isDefined) {
      val value = valueOpt.get
      if (value.isInstanceOf[Seq[_]]) {
        value.asInstanceOf[Seq[Any]]
      } else {
        throw new TypeValidationException(s"$parentId requires Map $targetId.$key to be a list, not $value")
      }
    } else {
      throw new TypeValidationException(s"$parentId requires Map $targetId to contain key $key")
    }
  }

  def mapGetToSeqThen[T](data: Map[Any, Any], key: Any, parentId: String, targetId: String)
   (func: (Seq[Any], String, String) => T): T = {
    val seq = mapGetToSeq(data, key, parentId, targetId)
    func(seq, parentId, s"$targetId.$key")
  }

  def seqToMap(data: Seq[Any], parentId: String, targetId: String): Seq[Map[Any, Any]] = {

    data.zipWithIndex.map(valueAndIndex => {
      val (value, index) = valueAndIndex
      if (value.isInstanceOf[Map[_, _]]) {
        value.asInstanceOf[Map[Any, Any]]
      } else {
        throw new TypeValidationException(s"$parentId requires $targetId[$index] to be a map, not $value")
      }
    })
  }

  def seqToMapThen[T](data: Seq[Any], parentId: String, targetId: String)
   (func: (Map[Any, Any], String, String) => T): Seq[T] = {
    seqToMap(data, parentId, targetId).zipWithIndex.map(valueAndIndex => {
      val (value, index) = valueAndIndex
      func(value, parentId, s"$targetId[$index]")
    })
  }

  def seqToSeqString(data: Seq[Any], parentId: String, targetId: String): Seq[String] = {

    data.zipWithIndex.map(valueAndIndex => {
      val (value, index) = valueAndIndex
      if (value.isInstanceOf[String]) {
        value.asInstanceOf[String]
      } else {
        throw new TypeValidationException(s"$parentId requires List $targetId[$index] to be a String, not $value")
      }
    })
  }

  def mapGetToString(data: Map[Any, Any], key: Any, parentId: String, targetId: String): String = {

    val valueOpt = data.get(key)
    if (valueOpt.isDefined) {
      val value = valueOpt.get
      if (value.isInstanceOf[String]) {
        value.asInstanceOf[String]
      } else {
        throw new TypeValidationException(s"$parentId requires Map $targetId.$key to be a String, not $value")
      }
    } else {
      throw new TypeValidationException(s"$parentId requires Map $targetId to contain key $key")
    }
  }

  def anyToMap(data: Any, parentId: String, targetId: String): Map[Any, Any] = {
    if (data.isInstanceOf[Map[_, _]]) {
      data.asInstanceOf[Map[Any, Any]]
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a Map, not $data")
    }
  }

  def anyToMapThen[T](data: Any, parentId: String, targetId: String)
   (func: (Map[Any, Any], String, String) => T): T = {
    val map = anyToMap(data, parentId, targetId)
    func(map, parentId, targetId)
  }

  def mapToMapStringString(data: Map[Any, Any], parentId: String, targetId: String): Map[String, String] = {
    data.foreach(entry => {
      if (entry._1.isInstanceOf[String]) {
        if (!entry._2.isInstanceOf[String]) {
          throw new TypeValidationException(s"$parentId requires the data of $targetId to be a String, not ${entry._2}")
        }
      } else {
        throw new TypeValidationException(s"$parentId requires the keys of $targetId to be a String, not ${entry._1}")
      }
    })
    data.asInstanceOf[Map[String, String]]
  }

  def anyToSeq(data: Any, parentId: String, targetId: String): Seq[Any] = {
    if (data.isInstanceOf[Seq[_]]) {
      data.asInstanceOf[Seq[Any]]
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a Seq, not $data")
    }
  }

  def anyToSeqThen[T](data: Any, parentId: String, targetId: String)
   (func: (Seq[Any], String, String) => T): T = {
    val seq = anyToSeq(data, parentId, targetId)
    func(seq, parentId, targetId)
  }

  def anyToString(data: Any, parentId: String, targetId: String): String = {
    if (data.isInstanceOf[String]) {
      data.asInstanceOf[String]
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a String, not $data")
    }
  }

  def anyToStringThen[T](data: Any, parentId: String, targetId: String)
   (func: (String, String, String) => T): T = {
    val string = anyToString(data, parentId, targetId)
    func(string, parentId, targetId)
  }

  def anyToInt(data: Any, parentId: String, targetId: String): Int = {
    if (data.isInstanceOf[Int]) {
      data.asInstanceOf[Int]
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a Int, not $data")
    }
  }

  def anyToLong(data: Any, parentId: String, targetId: String): Long = {
    if (data.isInstanceOf[Long]) {
      data.asInstanceOf[Long]
    } else if (data.isInstanceOf[Int]) {
      data.asInstanceOf[Int].toLong
    } else if (data.isInstanceOf[BigDecimal]) {
      data.asInstanceOf[BigDecimal].toLong
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a Long, not $data")
    }
  }

  def anyToBoolean(data: Any, parentId: String, targetId: String): Boolean = {
    if (data.isInstanceOf[Boolean]) {
      data.asInstanceOf[Boolean]
    } else {
      throw new TypeValidationException(s"$parentId requires $targetId to be a Boolean, not $data")
    }
  }

}