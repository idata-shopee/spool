package io.github.shopee.idata.spool

import scala.concurrent.{ ExecutionContext, Future }
import io.github.shopee.idata.klog.KLog
import java.util.UUID.randomUUID
import io.github.shopee.idata.taskqueue.TimeoutScheduler
import scala.collection.mutable.{ ListBuffer }
import java.util.concurrent.ConcurrentHashMap
import scala.collection.convert.decorateAsScala._
import scala.util.Random

/**
  *
  * usage of pool:
  *  1. backup - (manage a pool of connections)
  *  2. dynamic LB with config-service - (need a replace-in-time algorithm)
  *
  *
  * concepts:
  *  1. item - atom resource
  *  2. pool - a set of items
  */
object Pool {
  // like connection broken callback
  type OnItemBroken  = () => Unit
  type GetNewItem[U] = (OnItemBroken) => Future[Item[U]]
  type UseItem[U, T] = (U) => Future[T]

  val defaultClean = () => {}

  val POOL_ALIVE = 0
  val POOL_STOP  = 1
}

case class Item[U](resource: U, clean: () => Unit = Pool.defaultClean)

object ItemBox {
  val SUCC = 0
  val SUBS = 1
  val NOOP = 2

  val ITEM_NORMAL    = 0
  val ITEM_OUTOFDATE = 1
  val ITEM_CLEANED   = 2
}

case class ItemBox[U](id: String, item: Item[U]) {
  private var uses: Int  = 0
  private var state: Int = ItemBox.ITEM_NORMAL

  val createTime = System.currentTimeMillis()

  private def atomUses(t: Int): Boolean = synchronized {
    if (state == ItemBox.ITEM_CLEANED) {
      false
    } else {
      t match {
        case ItemBox.SUCC => uses += 1
        case ItemBox.SUBS => uses -= 1
        case ItemBox.NOOP => {}
      }

      // nobody use item and item is also out of date
      if (uses == 0 && state == ItemBox.ITEM_OUTOFDATE) {
        item.clean()
        state = ItemBox.ITEM_CLEANED
      }

      true
    }
  }

  def addUse(): Boolean =
    atomUses(ItemBox.SUCC)

  def removeUse(): Boolean =
    atomUses(ItemBox.SUBS)

  def dropItem() = synchronized {
    if (state != ItemBox.ITEM_CLEANED) {
      state = ItemBox.ITEM_OUTOFDATE
      atomUses(ItemBox.NOOP)
    }
  }
}

case class Pool[U](getNewItem: Pool.GetNewItem[U], size: Int = 8, freshTime: Int = 5 * 60 * 1000)(
    implicit ec: ExecutionContext
) {
  // maintain pool state, mainly used for tests
  /**********************************************************/
  private var poolState: Int = Pool.POOL_ALIVE
  private def setPoolState(state: Int) = synchronized {
    poolState = state
  }
  def stopPool() = {
    setPoolState(Pool.POOL_STOP)
    // clean pool
    itemBoxMap.keys.toList foreach { id =>
      itemBoxMap.get(id) match {
        case Some(itembox) => {
          itembox.item.clean()
          itemBoxMap.remove(id)
        }
        case None => {}
      }
    }
  }

  /**********************************************************/
  val r                  = Random
  private val itemBoxMap = new ConcurrentHashMap[String, ItemBox[U]]().asScala

  private def fetchItem(): Future[ItemBox[U]] = {
    val id = randomUUID().toString

    getNewItem(() => { // when item broken
      // remove item
      itemBoxMap.remove(id)
      TimeoutScheduler.sleep(1000) map { _ =>
        maintainPool()
      }
    }) map { item =>
      val itembox = ItemBox(id, item)
      itemBoxMap(id) = itembox
      itembox
    }
  }

  val maintainPool: () => Any = Once.once(() => {
    if (itemBoxMap.keys.size < size && poolState == Pool.POOL_ALIVE) {
      fetchItem() map { _ =>
        TimeoutScheduler.sleep(30 * 1000) map { _ =>
          maintainPool()
        }
      } recover {
        case e: Exception => {
          TimeoutScheduler.sleep(1000) map { _ =>
            maintainPool()
          }
        }
      }
    } else {
      Future {}
    }
  })

  def useItem[T](handle: Pool.UseItem[U, T], waitingTime: Int = 10 * 1000): Future[T] =
    pickItemBoxId(0, waitingTime / 1000) flatMap { id =>
      itemBoxMap.get(id) match {
        case Some(itembox) => {
          // add one more use
          // when add use success, item won't be cleaned in other threads
          if (itembox.addUse()) {
            try {
              val currentTime = System.currentTimeMillis()
              if (currentTime - itembox.createTime > freshTime && shouldRemove()) {
                itemBoxMap.remove(id)
                maintainPool()

                itembox.dropItem()
              }

              handle(itembox.item.resource) map { data =>
                itembox.removeUse()
                data
              } recover {
                case e: Exception => {
                  itembox.removeUse()
                  throw e
                }
              }
            } catch {
              case e: Exception => {
                KLog.logErr("use-item-sync-error", e)
                itembox.removeUse()
                throw e
              }
            }
          } else { // already cleaned
            useItem(handle, waitingTime)
          }
        }
        case None => useItem(handle, waitingTime)
      }
    }

  private def pickItemBoxId(retryCount: Int = 0, retryMax: Int = 0): Future[String] =
    if (retryCount > retryMax) {
      Future {
        throw new Exception("pool is empty!")
      }
    } else {
      val keys = itemBoxMap.keys.toList
      val size = keys.size
      if (size == 0) {
        TimeoutScheduler.sleep(1000) flatMap { _ =>
          pickItemBoxId(retryCount + 1, retryMax)
        }
      } else {
        val index = r.nextInt(size)
        Future {
          keys(index)
        }
      }
    }

  private def shouldRemove(): Boolean =
    itemBoxMap.keys.size > 1 && r.nextInt(size) == 0 // (1 / size) possibility

  maintainPool()
}
