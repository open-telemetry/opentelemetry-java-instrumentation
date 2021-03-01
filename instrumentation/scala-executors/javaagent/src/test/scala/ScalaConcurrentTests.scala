/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.javaagent.testing.common.Java8BytecodeBridge

import java.util.concurrent.CountDownLatch
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class ScalaConcurrentTests {
  val tracer: Tracer = GlobalOpenTelemetry.getTracer("test")

  /** @return Number of expected spans in the trace */
  def traceWithFutureAndCallbacks() {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
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
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def tracedAcrossThreadsWithNoTrace() {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val latch = new CountDownLatch(1)
      val goodFuture: Future[Integer] = Future {
        1
      }
      goodFuture onSuccess {
        case _ =>
          Future {
            2
          } onSuccess {
            case _ => {
              tracedChild("callback")
              latch.countDown()
            }
          }
      }

      latch.await()
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  /** @return Number of expected spans in the trace */
  def traceWithPromises() {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
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
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  /** @return Number of expected spans in the trace */
  def tracedWithFutureFirstCompletions() {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
      val completedVal = Future.firstCompletedOf(List(Future {
        tracedChild("timeout1")
        false
      }, Future {
        tracedChild("timeout2")
        false
      }, Future {
        tracedChild("timeout3")
        true
      }))
      Await.result(completedVal, 30 seconds)
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  /** @return Number of expected spans in the trace */
  def tracedTimeout(): Integer = {
    val parentSpan = tracer.spanBuilder("parent").startSpan()
    val parentScope =
      Java8BytecodeBridge.currentContext().`with`(parentSpan).makeCurrent()
    try {
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
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  def tracedChild(opName: String): Unit = {
    tracer.spanBuilder(opName).startSpan().end()
  }
}
