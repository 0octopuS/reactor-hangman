// group 22
// 101479682 Zhongxuan Xie
// 101603393 Martynas Krupskis

package reactor

import scala.collection.mutable.Map
import reactor.api.{Event, EventHandler, Handle}
import scala.util.control.Breaks._

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0) // Capacity must be positive.
  var eventQueue = new BlockingEventQueue[Any](queueLength) // z
  var workerThreadList: Map[EventHandler[_], WorkerThread[_]] = Map()

  @throws[InterruptedException]
  def handleEvents(): Unit = {
    var event: Event[_] = null
    var handler: EventHandler[_] = null
    // Repeatedly wait until an event is received from a registered handle and dispatch these events.
    while (workerThreadList.nonEmpty) {
      event = select
      // event = eventQueue.dequeue // Get the first event in the queue
      handler = event.getHandler // Get the handler of selected event
      if (workerThreadList.contains(handler)) {
        // If the handler is already in workerThreadList, then handle it
        event.dispatch()
      }
    }
  }

  // TODO: Tip from course =>  The reactor pattern logic that finds the next event is often called the select() method.
  // But here I think to select event, we only needs a dequeue. The following method can be used for replacement.
  def select(): Event[_] = {
    eventQueue.dequeue
  }

  def addHandler[T](handler: EventHandler[T]): Unit = {
    if (handler == null) throw new IllegalArgumentException()

    // A handler may be registered only once.
    if (!workerThreadList.contains(handler)) {
      // start dispatching incoming events for handler
      // The handler is added to the workerThreadList
      val thread = new WorkerThread[T](handler, eventQueue)
      workerThreadList += (handler -> thread)
      thread.start()
    }
  }

  def removeHandler[T](handler: EventHandler[T]): Unit = {
    if (handler == null) throw new IllegalArgumentException()

    // Stop dispatching incoming events for handler
    // The handler should be removed from the workerThreadList
    val thread = workerThreadList.remove(handler)
    // the remove method will return an `Option`
    // foreach => to deal with option, if it's not None then call cancelThread() method.
    thread.foreach(_.cancelThread())
  }

}

final class WorkerThread[T](
    private val handler: EventHandler[T],
    private val queue: BlockingEventQueue[Any]
) extends Thread {

  private var running: Boolean = true

  override def run(): Unit = {
    var handle: Handle[T] = handler.getHandle
    var event: Event[T] = null
    var data: T = null.asInstanceOf[T]

    while (running) {
      // Create event from the read data
      data = handle.read()
      event = new Event[T](data, handler)

      try {
        // Enqueue, but anticipate Interruption
        queue.enqueue(event)
      } catch {
        case e: InterruptedException => { throw (e) }
      }

      // Anticipate that the Handle will stop the handler.
      if (data == null) {
        running = false
      }
    }
  }

  def cancelThread(): Unit = {
    running = false
    super.interrupt()
    // super.isInterrupted()
    // interrupt()
  }
}
