// group 1
// 101479682 Zhongxuan Xie
// 654321 Firstname Lastname

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
//
//  TODO： This version is a busy-wait(worst solution)
//         Next step we need to add a waiting state
//
// !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
final class BlockingEventQueue[T](private val capacity: Int) {
  val queue = Queue[Event[T]]()
  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    if (e == null) { // Additional:  not accept null input to enqueue
      throw new IllegalArgumentException("Cannot enqueue null Event")
      return
    }
    try
      synchronized {
        while (queue.length == capacity) {
          wait() // Wait until the queue is not full
        }
        queue.enqueue(
          e.asInstanceOf[Event[T]]
        ) // add the specified event to the tail of the queue
        notifyAll() // weak other threads
      }
    catch {
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw (e) //  throw an InterruptedException
      }
    }
  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    try
      synchronized {

        while (queue.isEmpty) {
          wait() // Wait until the queue is non-empty
        }
        var front = queue.dequeue // remove the event from the head of the queue
        notifyAll() // weak other threads
        front // return it
      }
    catch {
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw (e) //  throw an InterruptedException
      }
    }
  }

  // getAll returns the entire current contents of the queue
  // TODO: the number if items getAll returns must have a well defined upper bound.
  def getAll: Seq[Event[T]] = {
    try
      synchronized {
        queue.dequeueAll((x: Event[T]) => true)
      }
    catch {
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw (e)
      }
    }
  }

  // getSize returns the current element number in the queue
  def getSize: Int = queue.length

  // getCapacity returns the maximum size of the queue
  def getCapacity: Int = capacity

}
