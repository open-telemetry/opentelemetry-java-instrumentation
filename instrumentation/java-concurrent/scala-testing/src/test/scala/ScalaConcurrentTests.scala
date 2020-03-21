/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import java.util.concurrent.CountDownLatch

import io.opentelemetry.OpenTelemetry
import io.opentelemetry.trace.Tracer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class ScalaConcurrentTests {
  val TRACER: Tracer = OpenTelemetry.getTracerProvider.get("io.opentelemetry.auto")

  /**
   * @return Number of expected spans in the trace
   */
  def traceWithFutureAndCallbacks() {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
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
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
    try {
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
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  /**
   * @return Number of expected spans in the trace
   */
  def traceWithPromises() {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
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

  /**
   * @return Number of expected spans in the trace
   */
  def tracedWithFutureFirstCompletions() {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
    try {
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
    } finally {
      parentSpan.end()
      parentScope.close()
    }
  }

  /**
   * @return Number of expected spans in the trace
   */
  def tracedTimeout(): Integer = {
    val parentSpan = TRACER.spanBuilder("parent").startSpan()
    val parentScope = TRACER.withSpan(parentSpan)
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
    TRACER.spanBuilder(opName).startSpan().end()
  }
}
