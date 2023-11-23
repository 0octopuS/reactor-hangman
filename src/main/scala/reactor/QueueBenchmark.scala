package reactor
// import pl.project13.scala.sbt.JmhPlugin
import reactor.api.{Event, EventHandler, Handle}
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra._
import org.openjdk.jmh.runner.IterationType
// import benchmark._
import java.util.concurrent.TimeUnit
import scala.collection.mutable.ListBuffer
import org.scalatest.time.{Seconds, Span}

@BenchmarkMode(Array(Mode.AverageTime))
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
class BlockingQueueBenchmark {

  // override def timeLimit: Span = Span(10, Seconds)

  class IntegerHandle(val i: Integer) extends Handle[Integer] {
    // def this() = { this(i) }
    override def read(): Integer = i
  }

  class IntegerHandler(h: Handle[Integer]) extends EventHandler[Integer] {
    override def getHandle: Handle[Integer] = h
    override def handleEvent(arg: Integer): Unit = {} // do nothing
  }

  def generateIntegerEvent(i: Integer): Event[Integer] = {
    val h = new IntegerHandle(i)
    Event(h.read(), new IntegerHandler(h))
  }

  ///////////////////////////////////////////////////////
  @Benchmark def with10Threads(blackHole: Blackhole) {
    val q = new BlockingEventQueue[Integer](2)

    val threadsNum: Int = 2 // create 2 threads
    val threadRuntimes: Int = 10 // Every thread rusn 10 times

    // The list of thread
    val threadList: ListBuffer[Thread] = ListBuffer.empty[Thread]

    // create two producer threads, put number 0~19 to queue
    // every thread puts 10 number
    for (i <- 0 until threadsNum) {
      val offset: Int = i * threadRuntimes
      val producer = new Thread(() => {
        try {
          for (j <- 0 until threadRuntimes) {
            val event = generateIntegerEvent(offset + j)
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
            // println(element.data)
          }
        } catch {
          case e: Exception =>
            e.printStackTrace()
        }
      })

      threadList += consumer
      consumer.start()
    }

    // 等待所有线程执行完成
    for (thread <- threadList) {
      thread.join()
    }
  }

  ///////////////////////////////////////////////////////

  ///////////////////////////////////////////////////////

}
