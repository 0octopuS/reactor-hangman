// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

final class BlockingEventQueue[T] (private val capacity: Int) {
  val queue = Queue[Event[T]]()
  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    if (e == null) {
    throw new IllegalArgumentException("Cannot enqueue null Event")
    return
    } 
    try synchronized {
      while(queue.length == capacity){
        wait()
      }
      queue.enqueue(e.asInstanceOf[Event[T]])
      notifyAll()
    } catch{
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw(e)
      }
    }
  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    try synchronized {
      while(queue.isEmpty){
        wait()
      }
      var front = queue.dequeue
      notifyAll()
      front
    } catch{
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw(e)
      }
    }
  }

  def getAll: Seq[Event[T]] = {
    try synchronized {
      queue.dequeueAll((x:Event[T]) => true)
    } catch{
      case e: InterruptedException => {
        println("Exception: interrupted")
        throw(e)
      }
    }
  }

  def getSize: Int = queue.length

  def getCapacity: Int = capacity

}