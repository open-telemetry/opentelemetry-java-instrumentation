import java.util.concurrent.CountDownLatch

import io.opentelemetry.auto.api.Trace
import io.opentelemetry.auto.instrumentation.api.AgentTracer.activeSpan

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class ScalaConcurrentTests {

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def traceWithFutureAndCallbacks() {
    val latch = new CountDownLatch(2)
    val goodFuture: Future[Integer] = Future {
      tracedChild("goodFuture")
      1
    }
    goodFuture onSuccess {
      case _ => {
        tracedChild("successCallback")
        latch.countDown()
      }
    }
    val badFuture: Future[Integer] = Future {
      tracedChild("badFuture")
      throw new RuntimeException("Uh-oh")
    }
    badFuture onFailure {
      case t: Throwable => {
        tracedChild("failureCallback")
        latch.countDown()
      }
    }

    latch.await()
  }

  @Trace
  def tracedAcrossThreadsWithNoTrace() {
    val latch = new CountDownLatch(1)
    val goodFuture: Future[Integer] = Future {
      1
    }
    goodFuture onSuccess {
      case _ => Future {
        2
      } onSuccess {
        case _ => {
          tracedChild("callback")
          latch.countDown()
        }
      }
    }

    latch.await()
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def traceWithPromises() {
    val keptPromise = Promise[Boolean]()
    val brokenPromise = Promise[Boolean]()
    val afterPromise = keptPromise.future
    val afterPromise2 = keptPromise.future

    val failedAfterPromise = brokenPromise.future

    Future {
      tracedChild("future1")
      keptPromise success true
      brokenPromise failure new IllegalStateException()
    }

    val latch = new CountDownLatch(3)
    afterPromise onSuccess {
      case b => {
        tracedChild("keptPromise")
        latch.countDown()
      }
    }
    afterPromise2 onSuccess {
      case b => {
        tracedChild("keptPromise2")
        latch.countDown()
      }
    }

    failedAfterPromise onFailure {
      case t => {
        tracedChild("brokenPromise")
        latch.countDown()
      }
    }

    latch.await()
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def tracedWithFutureFirstCompletions() {
    val completedVal = Future.firstCompletedOf(
      List(
        Future {
          tracedChild("timeout1")
          false
        },
        Future {
          tracedChild("timeout2")
          false
        },
        Future {
          tracedChild("timeout3")
          true
        }))
    Await.result(completedVal, 30 seconds)
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def tracedTimeout(): Integer = {
    val f: Future[String] = Future {
      tracedChild("timeoutChild")
      while (true) {
        // never actually finish
      }
      "done"
    }

    try {
      Await.result(f, 1 milliseconds)
    } catch {
      case e: Exception => {}
    }
    return 2
  }

  @Trace
  def tracedChild(opName: String): Unit = {
    activeSpan().setSpanName(opName)
  }
}
