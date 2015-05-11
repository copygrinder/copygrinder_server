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


import scala.collection.generic.{CanBuildFrom, ImmutableMapFactory}
import scala.collection.immutable.{HashMap, AbstractMap, MapLike}
import scala.collection.{AbstractIterator, GenTraversableOnce}

object ImmutableLinkedHashMap extends ImmutableMapFactory[ImmutableLinkedHashMap] {

  implicit def canBuildFrom[K, V]: CanBuildFrom[Coll, (K, V), ImmutableLinkedHashMap[K, V]] = new MapCanBuildFrom[K, V]

  def empty[K, V]: ImmutableLinkedHashMap[K, V] =
    EmptyImmutableLinkedHashMap$.asInstanceOf[ImmutableLinkedHashMap[K, V]]

  private object EmptyImmutableLinkedHashMap$ extends ImmutableLinkedHashMap[Any, Nothing] {}

}

class ImmutableLinkedHashMap[K, +V]
 extends AbstractMap[K, V]
 with Map[K, V]
 with MapLike[K, V, ImmutableLinkedHashMap[K, V]]
 with Serializable {

  protected val hashMap = new HashMap[K, ImmutableLinkedHashMapNode[K, V]]()

  protected val firstNodeKey = Option.empty[K]

  protected val lastNodeKey = Option.empty[K]

  override def empty = ImmutableLinkedHashMap.empty

  override def size = hashMap.size

  override def updated[V1 >: V](k: K, v: V1): ImmutableLinkedHashMap[K, V1] = {

    val nodeOpt = hashMap.get(k)
    if (nodeOpt.isDefined) {

      val node = nodeOpt.get
      val newHashMap = hashMap.updated(k, node.copy(value = v))

      copy(newHashMap)

    } else {
      addNew((k, v))
    }
  }

  protected def addNew[V1 >: V](kv: (K, V1)): ImmutableLinkedHashMap[K, V1] = {

    val key = kv._1
    val value = kv._2

    val tempHashMap = if (lastNodeKey.isDefined) {
      val currentLastNode = hashMap.get(lastNodeKey.get).get
      val newSecondLastNode = currentLastNode.copy(nextKey = Option(key))
      hashMap.updated(newSecondLastNode.key, newSecondLastNode)
    } else {
      hashMap
    }

    val newNode = ImmutableLinkedHashMapNode(key, value, lastNodeKey, None)
    val newHashMap = tempHashMap + (key -> newNode)

    val newFirstNodeKey = if (firstNodeKey.isEmpty) {
      Option(key)
    } else {
      firstNodeKey
    }

    val newLastNodeKey = Option(key)

    copy(newHashMap, newFirstNodeKey, newLastNodeKey)
  }

  override def +[V1 >: V](kv: (K, V1)): ImmutableLinkedHashMap[K, V1] = {
    updated(kv._1, kv._2)
  }

  override def ++[V1 >: V](xs: GenTraversableOnce[(K, V1)]): ImmutableLinkedHashMap[K, V1] =
    ((repr: ImmutableLinkedHashMap[K, V1]) /: xs.seq)(_ + _)

  override def get(key: K): Option[V] = {
    hashMap.get(key).map(_.value)
  }

  override def iterator: Iterator[(K, V)] = new AbstractIterator[(K, V)] {

    var currentNode = firstNodeKey.flatMap(hashMap.get(_))

    override def hasNext: Boolean = {
      currentNode.isDefined
    }

    override def next(): (K, V) = {
      val node = currentNode.get
      currentNode = node.nextKey.flatMap(hashMap.get(_))
      (node.key, node.value)
    }
  }


  override def -(key: K): ImmutableLinkedHashMap[K, V] = {

    val valueOpt = hashMap.get(key)

    if (valueOpt.isDefined) {

      val value = valueOpt.get

      val tempHashMap1 = correctPointer(hashMap, value.nextKey) { (newHashMap, k, node) =>
        newHashMap.updated(k, node.copy(previousKey = value.previousKey))
      }

      val tempHashMap2 = correctPointer(tempHashMap1, value.previousKey) { (newHashMap, k, node) =>
        newHashMap.updated(k, node.copy(nextKey = value.nextKey))
      }

      val newHashMap = tempHashMap2 - key

      val newLastNodeKey = correctKey(key, lastNodeKey)(_.previousKey)
      val newFirstNodeKey = correctKey(key, firstNodeKey)(_.nextKey)

      copy(newHashMap, newFirstNodeKey, newLastNodeKey)
    } else {
      this
    }

  }

  protected def correctKey(currentKey: K, targetKeyOpt: Option[K])
   (func: (ImmutableLinkedHashMapNode[K, V]) => Option[K]): Option[K] = {
    val targetKey = targetKeyOpt.get
    if (targetKey == currentKey) {
      val targetNode = hashMap.get(targetKey).get
      func(targetNode)
    } else {
      targetKeyOpt
    }
  }

  protected def correctPointer[V1](hashMap: HashMap[K, ImmutableLinkedHashMapNode[K, V1]], keyOpt: Option[K])
   (func: (HashMap[K, ImmutableLinkedHashMapNode[K, V1]], K, ImmutableLinkedHashMapNode[K, V1]) =>
    HashMap[K, ImmutableLinkedHashMapNode[K, V1]]) = {

    if (keyOpt.isDefined) {
      val key = keyOpt.get
      val node = hashMap.get(key).get
      func(hashMap, key, node)
    } else {
      hashMap
    }
  }

  protected def copy[V1](
   newHashMap: HashMap[K, ImmutableLinkedHashMapNode[K, V1]] = hashMap,
   newFirstNodeKey: Option[K] = firstNodeKey,
   newLastNodeKey: Option[K] = lastNodeKey
   ): ImmutableLinkedHashMap[K, V1] = {
    new ImmutableLinkedHashMap[K, V1] {
      override protected val hashMap = newHashMap
      override protected val firstNodeKey = newFirstNodeKey
      override protected val lastNodeKey = newLastNodeKey
    }
  }

  override def toString(): String = {
    hashMap.toString()
  }

}

protected case class ImmutableLinkedHashMapNode[K, +V](key: K, value: V, previousKey: Option[K], nextKey: Option[K])