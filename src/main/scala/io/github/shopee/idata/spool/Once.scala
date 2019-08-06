package io.github.free.lock.spool

import scala.concurrent.{ ExecutionContext, Future }
import java.util.concurrent.atomic.AtomicBoolean

object Once {

  /**
    * run only once
    */
  def once(fn: () => Future[_])(implicit ec: ExecutionContext) = {
    var busyFlag = new AtomicBoolean(false)

    () =>
      {
        if (busyFlag.compareAndSet(false, true)) {
          fn() map { _ =>
            busyFlag.set(false)
          } recover {
            case e: Exception => {
              busyFlag.set(false)
              throw e
            }
          }
        }
      }
  }
}
