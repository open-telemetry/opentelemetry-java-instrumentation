import datadog.trace.api.Trace
import datadog.trace.context.TraceScope
import io.opentracing.util.GlobalTracer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class ScalaConcurrentTests {

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def traceWithFutureAndCallbacks(): Integer = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
    val goodFuture: Future[Integer] = Future {
      tracedChild("goodFuture")
      1
    }
    goodFuture onSuccess {
      case _ => tracedChild("successCallback")
    }
    val badFuture: Future[Integer] = Future {
      tracedChild("badFuture")
      throw new RuntimeException("Uh-oh")
    }
    badFuture onFailure {
      case t: Throwable => tracedChild("failureCallback")
    }

    return 5
  }

  @Trace
  def tracedAcrossThreadsWithNoTrace(): Integer = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
    val goodFuture: Future[Integer] = Future {
      1
    }
    goodFuture onSuccess {
      case _ => Future {
        2
      } onSuccess {
        case _ => tracedChild("callback")
      }
    }

    return 2
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def traceWithPromises(): Integer = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
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

    afterPromise onSuccess {
      case b => tracedChild("keptPromise")
    }
    afterPromise2 onSuccess {
      case b => tracedChild("keptPromise2")
    }

    failedAfterPromise onFailure {
      case t => tracedChild("brokenPromise")
    }

    return 5
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def tracedWithFutureFirstCompletions(): Integer = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
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
    return 4
  }

  /**
    * @return Number of expected spans in the trace
    */
  @Trace
  def tracedTimeout(): Integer = {
    GlobalTracer.get().scopeManager().active().asInstanceOf[TraceScope].setAsyncPropagation(true)
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
    GlobalTracer.get().activeSpan().setOperationName(opName)
  }
}
