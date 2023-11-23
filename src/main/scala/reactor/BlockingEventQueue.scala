// group 1
// 101479682 Zhongxuan Xie
// 654321 Firstname Lastname

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue
import reactor.utils.BiSemaphore
import reactor.utils.Semaphore

final class BlockingEventQueue[T](private val capacity: Int) {
  require(capacity > 0)
  // The BlockingQueue has a bounded buffer,
  // So we use two semaphores notEmpty and notFull
  private val notEmpty = new Semaphore(0)
  private val notFull = new Semaphore(capacity)

  // Operate is a Binary Semaphore to guarantee
  // only one thread modify the current queue
  private val operate = new BiSemaphore()

  private val queue = Queue[Event[T]]()

  // enqueue method return the fist event in the queue
  // 1. Try to acquire the notFull semaphore.
  //    If success then the queue is not full and can be enqueued.
  // 2. Try to acquire the operate semaphore.
  //    If success then it's the only thread work on the queue
  // 3. operates
  // 4. release the operate semaphore so other thread can start to operated
  // 5. release the notEmpty semaphore

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // TODO: not sure how to deal with the exception here
    //      try every semaphore and throw?
    try {
      notFull.acquire()
    } catch {
      case e: InterruptedException => {
        throw (e)
      }
    }
    operate.acquire()
    queue.enqueue(e.asInstanceOf[Event[T]])
    notEmpty.release()
    operate.release()

  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    notEmpty.acquire()
    operate.acquire()
    val event = queue.dequeue()
    operate.release()
    notFull.release()
    event
  }

  @throws[InterruptedException]
  def getAll: Seq[Event[T]] = {
    notEmpty.acquireAll()
    operate.acquire()
    val event = queue.dequeueAll((x: Event[T]) => true)
    operate.release()
    notFull.releaseAll()
    event
  }

  @throws[InterruptedException]
  def getSize: Int = {
    operate.acquire()
    val size = queue.size
    operate.release()
    size
  }

  def getCapacity: Int = capacity
}
