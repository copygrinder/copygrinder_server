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
package org.copygrinder.pure.copybean.persistence.model

import java.io.{DataOutputStream, ByteArrayOutputStream}

import org.copygrinder.pure.copybean.exception.CopygrinderRuntimeException
import org.copygrinder.pure.copybean.persistence.HashFactoryWrapper

import scala.annotation.tailrec

case class TrieNode(
 id: Long = 0L,
 slots: IndexedSeq[Long] = IndexedSeq(),
 slotKeys: IndexedSeq[Option[BigInt]] = IndexedSeq(),
 bitmap: Long = 0L,
 overflowBitmap: Long = 0L,
 overflowCounts: IndexedSeq[Byte] = IndexedSeq(),
 level: Byte = 0) {

  protected lazy val nonPackedCount = java.lang.Long.bitCount(bitmap)

  def nodeSize(): Int = {
    slots.length
  }

  def get(key: BigInt): Option[(Long, Boolean)] = {
    getWithSlotIndex(key).map { case (value, isObj, slotIndex) => (value, isObj) }
  }

  protected def getWithSlotIndex(key: BigInt): Option[(Long, Boolean, Int)] = {

    val (mask, indexExists) = getMaskAndExists(key)

    if (indexExists) {

      val slotIndex = getSlotIndex(mask)

      val keyValueOpt = slotKeys(slotIndex)

      if (keyValueOpt.isDefined) {
        val keyValue = keyValueOpt.get
        if (keyValue == key) {
          Some(slots(slotIndex), true, slotIndex)
        } else {
          getFromOverflows(key, mask)
        }
      } else {
        Some(slots(slotIndex), false, slotIndex)
      }

    } else {
      None
    }

  }

  protected def getMaskAndExists(key: BigInt) = {

    val mask = getMask(key)
    val indexExists = (bitmap & mask) != 0

    (mask, indexExists)
  }

  protected def getMask(key: BigInt): Long = {
    val index = key.>>(level).&(0x3F).toLong
    val mask = 1L << index
    mask
  }

  protected def getSlotIndex(mask: Long): Int = {
    java.lang.Long.bitCount(bitmap & (mask - 1))
  }


  protected def getFromOverflows(key: BigInt, mask: Long): Option[(Long, Boolean, Int)] = {

    val overflowExists = doesOverflowExist(mask)

    if (overflowExists) {
      val (overflowCount, overflowStartIndex, _) = calcOverflowCountStartIndexAndOffset(
        mask, overflowBitmap, overflowCounts)

      val overflowIndexOpt = (overflowStartIndex to (overflowStartIndex + overflowCount - 1)).find { i =>
        val slotKey = slotKeys(i).getOrElse(
          throw new CopygrinderRuntimeException(s"Overflow slots must have keys but $i did not in ${this}.")
        )

        slotKey == key
      }

      if (overflowIndexOpt.isDefined) {
        val slotIndex = overflowIndexOpt.get
        Some((slots(slotIndex), true, slotIndex))
      } else {
        None
      }
    } else {
      None
    }

  }

  protected def doesOverflowExist(mask: Long): Boolean = {
    (overflowBitmap & mask) != 0
  }

  protected def calcOverflowCountStartIndexAndOffset(
   mask: Long, overflowBitmap: Long, overflowCounts: IndexedSeq[Byte]) = {

    val overflowOffset = java.lang.Long.bitCount(overflowBitmap & (mask - 1))
    val overflowCount = overflowCounts(overflowOffset)

    val overflowStartIndex = calcOverflowStartIndex(overflowOffset)

    (overflowCount, overflowStartIndex, overflowOffset)
  }

  protected def calcOverflowStartIndex(overflowOffset: Int): Int = {
    val otherOverflowsOffset = overflowCounts.take(overflowOffset).sum
    val overflowStartIndex = nonPackedCount + otherOverflowsOffset
    overflowStartIndex
  }

  def addOrGetNextNodeId(key: BigInt, value: Long): AddResult = {

    val existingValueOpt = getWithSlotIndex(key)

    if (existingValueOpt.isDefined) {
      val (existingValue, isObj, slotIndex) = existingValueOpt.get
      if (isObj) {
        val newSlots = slots.updated(slotIndex, value)
        AddResult.newNodeOnly(copyWithCalcId(slots = newSlots))
      } else {
        AddResult.triePointer(existingValue)
      }
    } else {
      if (slots.length < 64) {
        addToNode(key, value)
      } else {
        addToSubNode(key, value)
      }
    }

  }

  protected def addToNode(key: BigInt, value: Long): AddResult = {

    val (mask, indexExists) = getMaskAndExists(key)

    if (indexExists) {
      val overflowExists = doesOverflowExist(mask)

      val (newOverflowBitmap, newOverflowCounts1) = if (overflowExists) {
        overflowBitmap -> overflowCounts
      } else {
        val newOverflowBitmap = overflowBitmap | mask
        val overflowOffset = java.lang.Long.bitCount(overflowBitmap & (mask - 1))
        val (overflowCountsHead, overflowCountsRight) = overflowCounts.splitAt(overflowOffset)
        val newOverflowCounts = (overflowCountsHead :+ 0.toByte) ++ overflowCountsRight
        newOverflowBitmap -> newOverflowCounts
      }

      val (newOverflowCounts, newSlots, newSlotKeys) = calcNewNodeOverflowValues(
        key, value, mask, newOverflowBitmap, newOverflowCounts1)

      AddResult.newNodeOnly(
        copyWithCalcId(slots = newSlots, slotKeys = newSlotKeys, overflowCounts = newOverflowCounts,
          overflowBitmap = newOverflowBitmap)
      )

    } else {
      val newBitmap = bitmap | mask
      val slotIndex = java.lang.Long.bitCount(newBitmap & (mask - 1))

      val (newSlots, newSlotKeys) = insertKeyAndValue(key, value, slotIndex)
      AddResult.newNodeOnly(copyWithCalcId(slots = newSlots, slotKeys = newSlotKeys, bitmap = newBitmap))
    }

  }

  protected def calcNewNodeOverflowValues(
   key: BigInt, value: Long, mask: Long, newOverflowBitmap: Long, overflowCounts: IndexedSeq[Byte]) = {

    val (overflowCount, overflowStartIndex, overflowOffset) = calcOverflowCountStartIndexAndOffset(mask,
      newOverflowBitmap, overflowCounts)

    val newOverflowCount = overflowCount + 1
    val slotIndex = overflowStartIndex + overflowCount
    val newOverflowCounts = overflowCounts.updated(overflowOffset, newOverflowCount.toByte)

    val mainSlotIndex = getSlotIndex(mask)
    val mainSlotKey = slotKeys(mainSlotIndex).get

    val (newSlots, newSlotKeys) = if (key < mainSlotKey) {
      val mainSlotValue = slots(mainSlotIndex)
      val (tmpSlots, tmpSlotKeys) = insertKeyAndValue(mainSlotKey, mainSlotValue, overflowStartIndex)
      val updatedSlots = tmpSlots.updated(mainSlotIndex, value)
      val updatedKeys = tmpSlotKeys.updated(mainSlotIndex, Some(key))
      (updatedSlots, updatedKeys)
    } else {
      val sortedSlotIndex = (overflowStartIndex to slotIndex - 1).find { i =>
        key < slotKeys(i).get
      }.getOrElse(slotIndex)

      insertKeyAndValue(key, value, sortedSlotIndex)
    }

    (newOverflowCounts, newSlots, newSlotKeys)
  }


  protected def insertKeyAndValue(key: BigInt, value: Long, slotIndex: Int) = {

    val (slotsLeft, slotsRight) = slots.splitAt(slotIndex)
    val newSlots = (slotsLeft :+ value) ++ slotsRight

    val (slotKeysLeft, slotKeysRight) = slotKeys.splitAt(slotIndex)
    val newSlotKeys = (slotKeysLeft :+ Some(key)) ++ slotKeysRight

    (newSlots, newSlotKeys)
  }

  protected def addToSubNode(key: BigInt, value: Long): AddResult = {

    val (parentNode, subNode) = breakNodeIntoSubNode(key, value)

    val result1 = parentNode.addOrGetNextNodeId(key, value)

    if (result1.newNode.isDefined) {
      AddResult.newNodeAndSub(result1.newNode.get, Map(subNode.id -> subNode))
    } else {
      val result2 = subNode.addOrGetNextNodeId(key, value)
      if (result2.newNode.isDefined) {
        val newSubNode = result2.newNode.get
        val nestedSubNodes = result2.newSubNodes.getOrElse(Map())
        val slotIndex = parentNode.getSlotIndex(parentNode.getMask(key))
        val newParentNode = parentNode.copyWithCalcId(slots = parentNode.slots.updated(slotIndex, newSubNode.id))
        AddResult.newNodeAndSub(newParentNode, nestedSubNodes + (newSubNode.id -> newSubNode))
      } else {
        throw new CopygrinderRuntimeException("Tried adding to a newly created sub-node but got a link to another " +
         "node. This shouldn't be possible because new sub-nodes shouldn't have pointers.")
      }
    }

  }

  protected def breakNodeIntoSubNode(key: BigInt, value: Long): (TrieNode, TrieNode) = {

    val (slotIndex, removalKeys) = calcRemovalKeys()

    val removedKeysAndValues = removalKeys.map { removalKey =>
      val valueOpt = get(removalKey)
      if (valueOpt.isDefined) {
        removalKey -> valueOpt.get._1
      } else {
        get(removalKey)
        throw new CopygrinderRuntimeException(s"Removal key not found $removalKey.  This shouldn't happen.")
      }
    }

    val strippedTrie = removalKeys.foldLeft(this) { (result, removalKey) =>
      result.removeOrGetNextNodeId(removalKey).newNode.get
    }

    val baseSubNode = TrieNode(level = (level + 6).toByte)

    val subNode = removedKeysAndValues.foldLeft(baseSubNode) { case (result, (removedKey, removedValue)) =>
      result.addToNode(removedKey, removedValue).newNode.get
    }

    val tmpTrie = addTriePointer(strippedTrie, removalKeys.head, subNode.id, slotIndex)

    tmpTrie -> subNode
  }

  protected def calcRemovalKeys(): (Int, Iterable[BigInt]) = {
    if (overflowCounts.nonEmpty) {

      val (overflowCount, largestOverflowOffset) = overflowCounts.zipWithIndex.maxBy(_._1)

      val slotStartIndex = calcOverflowStartIndex(largestOverflowOffset)

      val overflowKeys = slotKeys.splitAt(slotStartIndex)._2.take(overflowCount).flatten

      val slotIndex = findOverflowSlotIndex(largestOverflowOffset)

      val removalKeys = slotKeys(slotIndex) ++ overflowKeys
      (slotIndex, removalKeys)
    } else {
      val slotIndex = slotKeys.indexWhere(_.isDefined)
      val removalKey = slotKeys(slotIndex).get
      (slotIndex, Seq(removalKey))
    }
  }

  protected def addTriePointer(node: TrieNode, key: BigInt, subNodeId: Long, slotIndex: Int) = {
    val tmpTrie = node.addToNode(key, subNodeId).newNode.get
    tmpTrie.copyWithCalcId(slotKeys = tmpTrie.slotKeys.updated(slotIndex, None))
  }

  @tailrec
  protected final def findOverflowSlotIndex(occurrences: Int, index: Int = 0): Int = {

    val newIndex = index + 1

    if (occurrences == 0) {
      index
    } else {
      val mask = 1L << newIndex
      val newOccurrences = if ((overflowBitmap & mask) == 1) {
        occurrences - 1
      } else {
        occurrences
      }
      findOverflowSlotIndex(newOccurrences, newIndex)
    }
  }

  def removeOrGetNextNodeId(key: BigInt): RemoveResult = {

    val valueOpt = getWithSlotIndex(key)

    if (valueOpt.isDefined) {
      val (existingValue, isObj, slotIndex) = valueOpt.get
      if (isObj) {
        val mask = getMask(key)
        val overflowExists = doesOverflowExist(mask)
        if (overflowExists) {
          removeFromOverflow(key, mask, slotIndex)
        } else {
          val newBitmap = bitmap ^ mask
          val (newSlots, newSlotKeys) = removeSlot(slots, slotKeys, slotIndex)
          RemoveResult.newNode(copyWithCalcId(bitmap = newBitmap, slotKeys = newSlotKeys, slots = newSlots))
        }
      } else {
        RemoveResult.triePointer(existingValue)
      }
    } else {
      RemoveResult.newNode(this)
    }

  }

  protected def removeFromOverflow(key: BigInt, mask: Long, slotIndex: Int): RemoveResult = {
    val (overflowCount, overflowStartIndex, overflowOffset) = calcOverflowCountStartIndexAndOffset(mask,
      overflowBitmap, overflowCounts)
    val (swappedSlots, swappedSlotKeys, swappedSlotIndex) = swapMainSlotWithOverflowIfNeeded(slots, slotKeys, key,
      slotIndex, overflowStartIndex)
    val (newSlots, newSlotKeys) = removeSlot(swappedSlots, swappedSlotKeys, swappedSlotIndex)
    if (overflowCount > 1) {
      val newOverflowCount = overflowCount - 1
      val newOverflowCounts = overflowCounts.updated(overflowOffset, newOverflowCount.toByte)
      RemoveResult.newNode(
        copyWithCalcId(slotKeys = newSlotKeys, slots = newSlots, overflowCounts = newOverflowCounts)
      )
    } else {
      val newOverflowBitmap = overflowBitmap ^ mask
      val (overflowCountsHead, overflowCountsRight) = overflowCounts.splitAt(overflowOffset)
      val newOverflowCounts = overflowCountsHead ++ overflowCountsRight.drop(1)
      RemoveResult.newNode(
        copyWithCalcId(slotKeys = newSlotKeys, slots = newSlots, overflowCounts = newOverflowCounts,
          overflowBitmap = newOverflowBitmap)
      )
    }
  }

  protected def swapMainSlotWithOverflowIfNeeded(oldSlots: IndexedSeq[Long], oldSlotKeys: IndexedSeq[Option[BigInt]],
   key: BigInt, slotIndex: Int, overflowSlotIndex: Int) = {
    if (oldSlotKeys(slotIndex).contains(key)) {

      val oldMainValue = oldSlots(slotIndex)
      val oldOverflowValue = oldSlots(overflowSlotIndex)

      val newSlots = oldSlots.updated(slotIndex, oldOverflowValue).updated(overflowSlotIndex, oldMainValue)

      val oldMainKey = oldSlotKeys(slotIndex)
      val oldOverflowKey = oldSlotKeys(overflowSlotIndex)

      val newSlotKeys = oldSlotKeys.updated(slotIndex, oldOverflowKey).updated(overflowSlotIndex, oldMainKey)

      (newSlots, newSlotKeys, overflowSlotIndex)
    } else {
      (oldSlots, oldSlotKeys, slotIndex)
    }
  }

  protected def removeSlot(slots: IndexedSeq[Long], slotKeys: IndexedSeq[Option[BigInt]], slotIndex: Int) = {

    val (slotsHead, slotsRight) = slots.splitAt(slotIndex)
    val newSlots = slotsHead ++ slotsRight.drop(1)

    val (slotKeysHead, slotKeysRight) = slotKeys.splitAt(slotIndex)
    val newSlotKeys = slotKeysHead ++ slotKeysRight.drop(1)

    (newSlots, newSlotKeys)
  }

  protected def copyWithCalcId(
   slots: IndexedSeq[Long] = slots,
   slotKeys: IndexedSeq[Option[BigInt]] = slotKeys,
   bitmap: Long = bitmap,
   overflowBitmap: Long = overflowBitmap,
   overflowCounts: IndexedSeq[Byte] = overflowCounts,
   level: Byte = level): TrieNode = {

    val hashBuilder = HashFactoryWrapper.newHash()

    val baos = new ByteArrayOutputStream()
    val dos = new DataOutputStream(baos)

    slots.foreach(dos.writeLong(_))
    slotKeys.flatten.foreach { key =>
      key.toByteArray.foreach(dos.writeLong(_))
    }
    dos.writeByte(level)

    dos.close()
    val bytes = baos.toByteArray

    hashBuilder.update(bytes, 0, bytes.length)
    val hash = hashBuilder.getValue

    new TrieNode(hash, slots, slotKeys, bitmap, overflowBitmap, overflowCounts, level)
  }

}


case class AddResult(newNodeAndOptionalSubNodeOrId: Either[(TrieNode, Map[Long, TrieNode]), Long]) {

  val newNode = if (newNodeAndOptionalSubNodeOrId.isLeft) {
    Some(newNodeAndOptionalSubNodeOrId.left.get._1)
  } else {
    None
  }

  val newSubNodes = if (newNodeAndOptionalSubNodeOrId.isLeft) {
    Some(newNodeAndOptionalSubNodeOrId.left.get._2)
  } else {
    None
  }

  val pointer = if (newNodeAndOptionalSubNodeOrId.isRight) {
    Some(newNodeAndOptionalSubNodeOrId.right.get)
  } else {
    None
  }

}

object AddResult {

  def newNodeOnly(node: TrieNode): AddResult = {
    AddResult(Left(node, Map.empty[Long, TrieNode]))
  }

  def newNodeAndSub(node: TrieNode, subNodes: Map[Long, TrieNode]): AddResult = {
    AddResult(Left(node, subNodes))
  }

  def triePointer(triePointer: Long): AddResult = {
    AddResult(Right(triePointer))
  }

}

case class RemoveResult(newNodeOrId: Either[TrieNode, Long]) {

  val newNode = if (newNodeOrId.isLeft) {
    Some(newNodeOrId.left.get)
  } else {
    None
  }

  val pointer = if (newNodeOrId.isRight) {
    Some(newNodeOrId.right.get)
  } else {
    None
  }

}

object RemoveResult {

  def newNode(node: TrieNode): RemoveResult = {
    RemoveResult(Left(node))
  }

  def triePointer(triePointer: Long): RemoveResult = {
    RemoveResult(Right(triePointer))
  }

}