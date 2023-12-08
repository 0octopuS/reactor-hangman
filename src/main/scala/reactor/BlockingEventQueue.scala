// group 1
// 101479682 Zhongxuan Xie
// 101603393 Martynas Krupskis

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
  // Note: Using two semaphore can guarantee that the thread only wait in either nonFull or nonEmpty state,
  //       rather than mixing them together

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // TODO: not sure how to deal with the exception here
    //      try every semaphore and throw?
    notFull.acquire()
    operate.acquire()
    queue.enqueue(e.asInstanceOf[Event[T]])
    operate.release()
    notEmpty.release()

  }

  // the reverse of enqueue
  // first acquire nonEmpty semaphore
  // release notFull after dequeue operation
  @throws[InterruptedException]
  def dequeue: Event[T] = {
    notEmpty.acquire()
    operate.acquire()
    val event = queue.dequeue()
    operate.release()
    notFull.release()
    event
  }

  // Because getAll method will dequeue all the elements in the queue
  // It needs to acquire and release all the value in the semaphores
  //
  @throws[InterruptedException]
  def getAll: Seq[Event[T]] = {
    var notEmptyValue = notEmpty.acquireAll()
    operate.acquire()
    // Problem:   To deal with the edge case, the value of semaphore cannot be
    //            directly rewrite to `0` or `capacity`
    // Solution:  Dynamically get the value of how many elements are dequeued,
    //            acquire and release the correct amount
    // ----------------------------------------------------------------------------------
    // Consideration 1:  Is there any other threads operating the queue?
    // This in within the `operate` semaphore, so no other threads are operating the queue
    // It's safe to use queue.size here.
    // ----------------------------------------------------------------------------------
    // Consideration 2:  What if the nonEmpty goes below zero?
    //     Mark this thread preforming `getAll` as D1
    //     if dequeue more elements than the notEmptyValue
    //     it means some threads(E1, E2, ...) enqueue after D1 acquire all notEmpty semaphore
    // ----------------------------------------------------------------------------------
    // Consideration 3: Is this safe to operate on nonEmpty.permit?
    //    First acquireBelowZero use synchronized, so the operation in permits should be safe
    //    >> A. Case if other threads want to acquire:
    //          After `acquireAll`, the nonEmpty will be zero.
    //          So all the new producer are wait in nonEmpty, and no one can acquire new.
    //    >> B. Case if other threads want to release:
    //       >>>  B.1 release after `acquireBelowZero`
    //                this makes notEmpty go up by one
    //       >>>  B.2 release before `acquireBelowZero`
    //                !!! can result in another consumer thread enter nonEmpty zone
    //                    But noting to dequeue.
    val size = queue.size
    if (size > notEmptyValue) {
      notEmpty.acquireBelowZero(
        size - notEmptyValue
      ) // make notEmpty value temporarily be negative
      // E1, E2, ... with release notEmpty in the end, so it will go back to zero.
      notEmptyValue = size
    }
    val event = queue.dequeueAll((x: Event[T]) => true)
    operate.release()

    // releaseAll takes the true dequeue amount, and add it to the notFull semaphore.
    notFull.releaseAll(notEmptyValue)
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
