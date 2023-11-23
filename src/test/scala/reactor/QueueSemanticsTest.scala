package reactor

import org.scalatest.concurrent.TimeLimitedTests
import reactor.api.{Event, EventHandler, Handle}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Seconds, Span}
import scala.collection.mutable.ListBuffer
import java.util.concurrent.{
  Callable,
  CountDownLatch,
  Executors,
  Future,
  ThreadLocalRandom
}

class QueueSemanticsTest extends AnyFunSuite with TimeLimitedTests {

  // The time limit is arbitrary and dependent on the computer
  override def timeLimit: Span = Span(10, Seconds)

  class IntegerHandle(val i: Integer) extends Handle[Integer] {
    def this() = { this(scala.util.Random.nextInt()) }
    override def read(): Integer = scala.util.Random.nextInt()
  }

  class IntegerHandler(h: Handle[Integer]) extends EventHandler[Integer] {
    override def getHandle: Handle[Integer] = h
    override def handleEvent(arg: Integer): Unit = {} // do nothing
  }

  def generateIntegerEvent: Event[Integer] = {
    val h = new IntegerHandle()
    Event(h.read(), new IntegerHandler(h))
  }

  class FixIntegerHandle(val i: Integer) extends Handle[Integer] {
    // def this() = { this(i) }
    override def read(): Integer = i
  }

  def generateFixIntegerEvent(i: Integer): Event[Integer] = {
    val h = new FixIntegerHandle(i)
    Event(h.read(), new IntegerHandler(h))
  }

  test("the queue is empty when created") {
    val q = new BlockingEventQueue[Integer](10)

    assert(q.getCapacity === 10)
    assert(q.getSize === 0)
  }

  test("the queue returns inserted elements") {
    val q = new BlockingEventQueue[Integer](10)

    val e = generateIntegerEvent
    q.enqueue(e)

    assert(q.getSize == 1)
    assert(q.dequeue === e)
  }

  test("the queue retains the order of elements") {
    val q = new BlockingEventQueue[Integer](10)
    val e1 = generateIntegerEvent
    val e2 = generateIntegerEvent
    val e3 = generateIntegerEvent
    val e4: Event[Integer] = Event(null, new IntegerHandler(null))

    q.enqueue(e1)
    q.enqueue(e2)
    q.enqueue(e3)
    q.enqueue(e4) // add data = null test

    assert(q.getSize === 4)
    assert(q.dequeue === e1)
    assert(q.dequeue === e2)
    assert(q.dequeue === e3)
    assert(q.dequeue === e4)
  }

  test("the queue implements getAll") {
    val q = new BlockingEventQueue[Integer](10)
    val e1 = generateIntegerEvent
    val e2 = generateIntegerEvent
    val e3 = generateIntegerEvent

    q.enqueue(e1)
    q.enqueue(e2)
    q.enqueue(e3)

    val everything = q.getAll

    assert(q.getSize === 0)
    assert(everything.length === 3)
    assert(everything(0) === e1)
    assert(everything(1) === e2)
    assert(everything(2) === e3)
  }

  test("Time spend with many threads") {
    val q = new BlockingEventQueue[Integer](2)

    val threadsNum: Int = 5000 // create x threads
    val threadRuntimes: Int = 10 // Every thread runs y times

    // The list of thread
    val threadList: ListBuffer[Thread] = ListBuffer.empty[Thread]
    // var appearNum: Array[Boolean] =
    //   Array.fill(threadsNum * threadRuntimes)(false)
    // create two producer threads, put number 0~19 to queue
    // every thread puts 10 number
    val startTime: Long = System.currentTimeMillis()
    for (i <- 0 until threadsNum) {
      val offset: Int = i * threadRuntimes
      val producer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val event = generateFixIntegerEvent(offset + j)
            q.enqueue(event)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += producer
      producer.start()
    }

    // create 2 consumer thread, dequeue and print these numbers
    for (i <- 0 until threadsNum) {
      val consumer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val element = q.dequeue
            // appearNum.update(element.getData, true)
            // println(element.getData)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += consumer
      consumer.start()
    }

    for (i <- 0 until threadsNum) {
      val outsider = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val nowSize = q.getSize
            // appearNum.update(element.getData, true)
            // println(element.getData)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += outsider
      outsider.start()
    }

    for (thread <- threadList) {
      thread.join() // Wait for all thread finishing
    }
    val endTime: Long = System.currentTimeMillis()
    println(f"Timespend: ${(endTime - startTime) / 1e3}%.2fs")

    // for (i <- 0 to (appearNum.length - 1)) {
    //   if (appearNum(i) == false) {
    //   println("No appear", i, appearNum(i))
    //   }
    // }
    // assert(!appearNum.contains(false))
  }

  test("Consumer and producer not symmetric case") {
    val q = new BlockingEventQueue[Integer](2000)

    val threadsNum: Int = 200 // create 2 threads
    val threadRuntimes: Int = 10 // Every thread runs 10 times

    // The list of thread
    val threadList: ListBuffer[Thread] = ListBuffer.empty[Thread]
    for (i <- 0 until threadsNum) {
      val offset: Int = i * threadRuntimes
      val producer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val event = generateFixIntegerEvent(offset + j)
            q.enqueue(event)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += producer
      producer.start()
    }
    for (thread <- threadList) {
      thread.join() // Wait for all thread finishing
    }

    threadList.clear()
    println("Pre-write End")
    val startTime: Long = System.currentTimeMillis()
    for (i <- 0 until threadsNum) {
      val offset: Int = i * threadRuntimes
      val producer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val event = generateFixIntegerEvent(offset + j)
            q.enqueue(event)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += producer
      producer.start()
    }

    // create 2 consumer thread, dequeue and print these numbers
    for (i <- 0 until threadsNum * 2) {
      val consumer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val element = q.dequeue
            // appearNum.update(element.getData, true)
            // println(element.getData)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += consumer
      consumer.start()
    }

    for (thread <- threadList) {
      thread.join() // Wait for all thread finishing
    }
    // println(f"Timespend: ${(endTime - startTime) / 1e3}%.2fs")
    assert(q.getSize != threadsNum * threadRuntimes)

  }

  test("Parallel Insertion And Consumption Test ") {
    val NUM_THREADS = 3

    val queue = new BlockingEventQueue[Integer](1000)
    val threadPool = Executors.newFixedThreadPool(NUM_THREADS)
    val latch = new CountDownLatch(NUM_THREADS)

    val futuresPut = (1 to 3).map { _ =>
      threadPool.submit(new Callable[Integer] {
        def call(): Integer = {
          var sum = 0
          for (_ <- 1 to 300) {
            val nextInt = ThreadLocalRandom.current().nextInt(100)
            val event = generateFixIntegerEvent(nextInt)
            queue.enqueue(event)
            sum += nextInt
          }
          latch.countDown()
          sum
        }
      })
    }

    val futuresGet = (1 to 3).map { _ =>
      threadPool.submit(new Callable[Integer] {
        def call(): Integer = {
          var count = 0
          try {
            for (_ <- 1 to 300) {
              val got = queue.dequeue
              count += got.getData
            }
          } catch {
            case _: InterruptedException =>
          }
          latch.countDown()
          count
        }
      })
    }

    latch.await()

    val sumPut = futuresPut.map(_.get()).map(_.toInt).sum
    val sumGet = futuresGet.map(_.get()).map(_.toInt).sum

    assert(sumPut == sumGet)
  }
}
