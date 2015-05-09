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
package org.copygrinder.pure.collections


import scala.collection.{GenTraversableOnce, AbstractIterator}
import scala.collection.generic.{CanBuildFrom, ImmutableMapFactory}
import scala.collection.immutable.{ListMap, AbstractMap, HashMap, MapLike}

object IndexedHashMap extends ImmutableMapFactory[IndexedHashMap] {
  implicit def canBuildFrom[A, B]: CanBuildFrom[Coll, (A, B), IndexedHashMap[A, B]] = new MapCanBuildFrom[A, B]

  def empty[A, B]: IndexedHashMap[A, B] = EmptyIndexedHashMap.asInstanceOf[IndexedHashMap[A, B]]

  private object EmptyIndexedHashMap extends IndexedHashMap[Any, Nothing] {}

}

class IndexedHashMap[A, +B]
 extends AbstractMap[A, B]
 with Map[A, B]
 with MapLike[A, B, IndexedHashMap[A, B]]
 with Serializable {

  protected val hashMap = new HashMap[A, B]()

  protected val order = Vector[A]()

  override def empty = IndexedHashMap.empty

  override def size = hashMap.size

  override def updated[B1 >: B](k: A, v: B1): IndexedHashMap[A, B1] = {

    val valueOpt = hashMap.get(k)
    if (valueOpt.isDefined) {

      val newHashMap = hashMap.updated(k, v)
      val outerOrder = order

      new IndexedHashMap[A, B1] {
        override protected val hashMap = newHashMap
        override protected val order = outerOrder
      }

    } else {
      addNew((k, v))
    }

  }

  protected def addNew[B1 >: B](kv: (A, B1)): IndexedHashMap[A, B1] = {

    val key = kv._1

    val newHashMap = hashMap + kv
    val newOrder = order :+ key

    new IndexedHashMap[A, B1] {
      override protected val hashMap = newHashMap
      override protected val order = newOrder
    }
  }

  override def +[B1 >: B](kv: (A, B1)): IndexedHashMap[A, B1] = {
    updated(kv._1, kv._2)
  }

  override def ++[B1 >: B](xs: GenTraversableOnce[(A, B1)]): IndexedHashMap[A, B1] =
    ((repr: IndexedHashMap[A, B1]) /: xs.seq)(_ + _)

  override def get(key: A): Option[B] = {
    hashMap.get(key)
  }

  override def iterator: Iterator[(A, B)] = new AbstractIterator[(A, B)] {

    val iter = order.iterator

    override def hasNext: Boolean = {
      iter.hasNext
    }

    override def next(): (A, B) = {
      val key = iter.next()
      (key, hashMap.get(key).get)
    }

  }


  override def -(key: A): IndexedHashMap[A, B] = {

    val valueOpt = hashMap.get(key)

    if (valueOpt.isDefined) {
      val newHashMap = hashMap - key
      val index = order.indexOf(key)
      val newOrder = order.slice(index, index + 1)

      new IndexedHashMap[A, B] {
        override protected val hashMap = newHashMap
        override protected val order = newOrder
      }
    } else {
      this
    }

  }

}