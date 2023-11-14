// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package reactor

import scala.collection.mutable.Map
import reactor.api.{Event, EventHandler, Handle}
import scala.util.control.Breaks._

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)
  var eventQueue = new BlockingEventQueue[Any](queueLength)
  var workerThreadList: Map[EventHandler[_], WorkerThread[_]] = Map()

  @throws[InterruptedException]
  def handleEvents(): Unit = {

    var selectEvent: Event[_] = null
    var selectEventHandler: EventHandler[_] = null

    while (workerThreadList.nonEmpty) {
      selectEvent = eventQueue.dequeue
      selectEventHandler = selectEvent.getHandler
      if (!workerThreadList.contains(selectEventHandler)) {
        break
      } else {
        selectEvent.dispatch()
      }
    }
  }

  def addHandler[T](handler: EventHandler[T]): Unit = {
    if (handler == null) throw new IllegalArgumentException()

    if (!workerThreadList.contains(handler)) {
      val thread = new WorkerThread[T](handler, eventQueue)
      workerThreadList += (handler -> thread)
      thread.start()
    }
  }

  def removeHandler[T](handler: EventHandler[T]): Unit = {
    if (handler == null) throw new IllegalArgumentException()

    val thread = workerThreadList.remove(handler)
    thread.foreach(_.cancelThread())
  }

}

// Hint:
final class WorkerThread[T](
    private val handler: EventHandler[T],
    private val queue: BlockingEventQueue[Any]
) extends Thread {

  @volatile private var running: Boolean = true

  override def run(): Unit = {
    var handle: Handle[T] = handler.getHandle
    var event: Event[T] = null
    var data: T = null.asInstanceOf[T]

    while (running) {
      data = handle.read()
      event = new Event[T](data, handler)

      try {
        queue.enqueue(event)
      } catch {
        case e: InterruptedException => return
      }

      if (data == null) {
        running = false
      }
    }
  }

  def cancelThread(): Unit = {
    running = false
    interrupt()
  }
}
