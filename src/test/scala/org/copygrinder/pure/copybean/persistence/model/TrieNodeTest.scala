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

import org.copygrinder.UnitTest

class TrieNodeTest extends UnitTest {

  val baseNode = new TrieNode(keyLength = 66)

  val baseKey = 100000000000000000L

  "A TrieNode" should "add and retrieve elements" in {

    val newNode = baseNode.addOrGetNextNodeId(baseKey, 1L).newNode.get

    assert(newNode.get(baseKey) == Option(1L, true))
  }

  it should "should overflow collisions when the node isn't full" in {

    val newNode1 = baseNode.addOrGetNextNodeId(baseKey, 1L).newNode.get

    val key2 = baseKey * 2

    val newNode2 = newNode1.addOrGetNextNodeId(key2, 2L).newNode.get

    val key3 = baseKey + 1

    val newNode3 = newNode2.addOrGetNextNodeId(key3, 3L).newNode.get

    val key4 = key2 + 1

    val newNode4 = newNode3.addOrGetNextNodeId(key4, 4L).newNode.get

    val key5 = baseKey * 4

    val newNode5 = newNode4.addOrGetNextNodeId(key5, 5L).newNode.get

    val key6 = baseKey * 3

    val newNode6 = newNode5.addOrGetNextNodeId(key6, 6L).newNode.get

    val key7 = baseKey + 5

    val newNode7 = newNode6.addOrGetNextNodeId(key7, 7L).newNode.get

    val key8 = baseKey + 4

    val newNode8 = newNode7.addOrGetNextNodeId(key8, 8L).newNode.get

    assert(newNode8.get(baseKey) == Option(1L, true))
    assert(newNode8.get(key2) == Option(2L, true))
    assert(newNode8.get(key3) == Option(3L, true))
    assert(newNode8.get(key4) == Option(4L, true))
    assert(newNode8.get(key5) == Option(5L, true))
    assert(newNode8.get(key6) == Option(6L, true))
    assert(newNode8.get(key7) == Option(7L, true))
    assert(newNode8.get(key8) == Option(8L, true))
  }

  it should "should update existing keys" in {

    val key = 63L

    val newNode1 = baseNode.addOrGetNextNodeId(key, 2L).newNode.get
    val newNode2 = newNode1.addOrGetNextNodeId(key, 1L).newNode.get

    assert(newNode2.get(key) == Option(1L, true))
  }

  it should "should remove existing keys" in {

    val key = 64L

    val newNode1 = baseNode.addOrGetNextNodeId(key, 1L).newNode.get
    val newNode2 = newNode1.removeOrGetNextNodeId(key).newNode.get

    assert(newNode1.get(key).nonEmpty)
    assert(newNode2.get(key).isEmpty)
  }

  it should "should remove overflowing keys" in {

    val key1 = baseKey
    val key2 = baseKey * 2
    val key3 = baseKey * 3

    val newNode1 = baseNode.addOrGetNextNodeId(key1, 1L).newNode.get
    val newNode2 = newNode1.addOrGetNextNodeId(key2, 2L).newNode.get
    val newNode3 = newNode2.addOrGetNextNodeId(key3, 3L).newNode.get

    val newNode4_1 = newNode3.removeOrGetNextNodeId(key1).newNode.get

    assert(newNode4_1.get(key1).isEmpty)
    assert(newNode4_1.get(key2).nonEmpty)
    assert(newNode4_1.get(key3).nonEmpty)

    val newNode4_2 = newNode3.removeOrGetNextNodeId(key2).newNode.get

    assert(newNode4_2.get(key1).nonEmpty)
    assert(newNode4_2.get(key2).isEmpty)
    assert(newNode4_2.get(key3).nonEmpty)

    val newNode4_3 = newNode3.removeOrGetNextNodeId(key3).newNode.get

    assert(newNode4_3.get(key1).nonEmpty)
    assert(newNode4_3.get(key2).nonEmpty)
    assert(newNode4_3.get(key3).isEmpty)

    val newNode5_1 = newNode4_1.removeOrGetNextNodeId(key2).newNode.get

    assert(newNode5_1.get(key2).isEmpty)
    assert(newNode5_1.get(key3).nonEmpty)

    val newNode5_2 = newNode4_1.removeOrGetNextNodeId(key3).newNode.get

    assert(newNode5_2.get(key2).nonEmpty)
    assert(newNode5_2.get(key3).isEmpty)

    val newNode6 = newNode5_1.removeOrGetNextNodeId(key3).newNode.get
    assert(newNode6.nodeSize() == 0)
  }

  it should "should create sub nodes" in {

    val fullNode = (1 to 64).foldLeft(baseNode) { (result, i) =>
      val key = baseKey + i
      result.addOrGetNextNodeId(key, i).newNode.get
    }

    val key2 = baseKey + 65

    val subNodeResult = fullNode.addOrGetNextNodeId(key2, 65L)

    assert(subNodeResult.newNode.isDefined)
    assert(subNodeResult.newSubNodes.isDefined)

    val parentNode = subNodeResult.newNode.get
    val subNodes = subNodeResult.newSubNodes.get

    assert(subNodes.size == 10)

    assert(getNestedValue(key2, parentNode, subNodes) == 65L)

    val key3 = baseKey + 66

    val subNodeResult2 = fullNode.addOrGetNextNodeId(key3, 66L)

    val parentNode2 = subNodeResult2.newNode.get
    val subNodes2 = subNodeResult2.newSubNodes.get

    assert(getNestedValue(key3, parentNode2, subNodes2) == 66L)
  }

  protected final def getNestedValue(key: Long, parentNode: TrieNode, subNodes: Map[Long, TrieNode]): Long = {
    val result = parentNode.get(key).get
    if (result._2) {
      result._1
    } else {
      getNestedValue(key, subNodes.get(result._1).get, subNodes)
    }
  }

  it should "should create sub nodes from parents with no overflows" in {
    val fullNode = (0 to 63).foldLeft(baseNode) { (result, i) =>
      val key = baseKey + i
      result.addOrGetNextNodeId(key, i).newNode.get
    }

    val key = baseKey + 64
    val result = fullNode.addOrGetNextNodeId(key, 64)

    assert(getNestedValue(key, result.newNode.get, result.newSubNodes.get) == 64)
  }

  it should "should maintain keys in order" in {

    val nonOverflowNode = (31 to 0 by -1).foldLeft(baseNode) { (result, i) =>
      val key = baseKey + i
      result.addOrGetNextNodeId(key, i).newNode.get
    }

    val fullNonOverflowNode = (32 to 63).foldLeft(nonOverflowNode) { (result, i) =>
      val key = baseKey + i
      result.addOrGetNextNodeId(key, i).newNode.get
    }

    assert(fullNonOverflowNode.slots.head == 0L)
    assert(fullNonOverflowNode.slots.last == 63L)

    val overflowNode = (63 to 0 by -1).foldLeft(baseNode) { (result, i) =>
      val key = baseKey * i
      result.addOrGetNextNodeId(key, i).newNode.get
    }


    assert(overflowNode.slots.head == 0)
    assert(overflowNode.slots.last == 63)
  }

}