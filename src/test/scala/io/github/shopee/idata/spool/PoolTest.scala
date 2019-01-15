package io.github.shopee.idata.spool

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ Await, Future, Promise, duration }
import duration._

class PoolTest extends org.scalatest.FunSuite {
  test("base") {
    val pool = Pool[Int]((onClose) => Future { Item(1) }, 8)
    pool.useItem((v) => Future { v + 1 })
  }

  test("item box") {
    1 to 100 foreach { _ =>
      var cleaned = false
      val itemBox = ItemBox[Int]("a", Item(10, () => {
        // do clean
        cleaned = !cleaned
      }))

      Await.result(Future.sequence(
                     List(
                       Future {
                         1 to 10000 foreach { _ =>
                           itemBox.addUse()
                         }
                         itemBox.dropItem()
                       },
                       Future {
                         1 to 10000 foreach { _ =>
                           itemBox.removeUse()
                         }
                       }
                     )
                   ),
                   15.seconds)

      assert(cleaned == true)
    }
  }
}
