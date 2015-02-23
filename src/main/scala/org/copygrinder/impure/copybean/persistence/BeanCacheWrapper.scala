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
package org.copygrinder.impure.copybean.persistence

import org.copygrinder.pure.copybean.model.ReifiedCopybean
import spray.caching.{Cache, LruCache, ValueMagnet}

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}


class BeanCacheWrapper {

  lazy val beanCache: Cache[ReifiedCopybean] = LruCache()

  val typeIdToBeanIds = new TrieMap[String, Set[String]]

  def apply(beanId: String): beanCache.Keyed = {

    new beanCache.Keyed(beanId) {
      override def apply(magnet: ⇒ ValueMagnet[ReifiedCopybean])(implicit ec: ExecutionContext): Future[ReifiedCopybean] = {
        super.apply(magnet).map(bean => {

          bean.enforcedTypeIds.foreach(typeId => {
            val beanSet = typeIdToBeanIds.getOrElse(typeId, Set[String]())
            typeIdToBeanIds.put(typeId, beanSet + beanId)
          })

          bean
        })
      }
    }
  }

  def remove(beanId: String)(implicit ec: ExecutionContext): Option[Future[ReifiedCopybean]] = {
    val mapFutureOpt = beanCache.get(beanId).map(future => {
      future.map(bean => {
        bean.enforcedTypeIds.foreach(typeId => {
          typeIdToBeanIds.get(typeId).map(beans => {
            typeIdToBeanIds.put(typeId, beans - beanId)
          })
        })
      })
    })

    mapFutureOpt.map(mapFuture => {
      Await.result(mapFuture, 5 seconds)
    })

    beanCache.remove(beanId)
  }

  def invalidateBeansOfType(typeId: String): Unit = {
    typeIdToBeanIds.get(typeId).map(beanIds => {
      beanIds.foreach(beanId => {
        beanCache.remove(beanId)
      })
    })
    typeIdToBeanIds.put(typeId, Set())
  }

}
