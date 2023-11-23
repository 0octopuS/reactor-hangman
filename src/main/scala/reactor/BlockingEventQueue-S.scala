// group 1
// 101479682 Zhongxuan Xie
// 654321 Firstname Lastname

package reactor2

import reactor.api.Event
import scala.collection.mutable.Queue
import reactor.utils.Semaphore

final class BlockingEventQueue[T](private val capacity: Int) {
  val queue = new Queue[Event[T]]()
  val capacityQ = capacity

  val capacitySem = new Semaphore(capacity)
  val itemsSem = new Semaphore(0)
  val mutation = new Semaphore(1)

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    capacitySem.acquire();
    mutation.acquire();
    queue.enqueue(e.asInstanceOf[Event[T]]);
    mutation.release();
    itemsSem.release();
  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    itemsSem.acquire();
    mutation.acquire()
    val e = queue.dequeue()
    mutation.release()
    capacitySem.release()
    e
  }

  def getAll: Seq[Event[T]] = {
    mutation.acquire()
    val events = queue.dequeueAll(_ => true)
    mutation.release()
    events
  }

  def getSize: Int = {
    mutation.acquire()
    val size = queue.size
    mutation.release()
    size
  }

  def getCapacity: Int = {
    capacity
  }

}
