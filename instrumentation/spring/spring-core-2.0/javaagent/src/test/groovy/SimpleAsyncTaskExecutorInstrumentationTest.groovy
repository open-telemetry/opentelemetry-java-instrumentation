/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.springframework.core.task.SimpleAsyncTaskExecutor
import spock.lang.Shared
import spock.lang.Unroll

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class SimpleAsyncTaskExecutorInstrumentationTest extends AgentInstrumentationSpecification {

  @Shared
  def executeRunnable = { e, c -> e.execute((Runnable) c) }
  @Shared
  def submitRunnable = { e, c -> e.submit((Runnable) c) }
  @Shared
  def submitCallable = { e, c -> e.submit((Callable) c) }
  @Shared
  def submitListenableRunnable = { e, c -> e.submitListenable((Runnable) c) }
  @Shared
  def submitListenableCallable = { e, c -> e.submitListenable((Callable) c) }

  @Unroll
  def "should propagate context on #desc"() {
    given:
    def executor = new SimpleAsyncTaskExecutor()

    when:
    runWithSpan("parent") {
      def child1 = new AsyncTask(startSpan: true)
      def child2 = new AsyncTask(startSpan: false)
      method(executor, child1)
      method(executor, child2)
      child1.waitForCompletion()
      child2.waitForCompletion()
    }

    then:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "parent"
          kind INTERNAL
        }
        span(1) {
          name "asyncChild"
          kind INTERNAL
          childOf(span(0))
        }
      }
    }

    where:
    desc                        | method
    "execute Runnable"          | executeRunnable
    "submit Runnable"           | submitRunnable
    "submit Callable"           | submitCallable
    "submitListenable Runnable" | submitListenableRunnable
    "submitListenable Callable" | submitListenableCallable
  }
}

class AsyncTask implements Runnable, Callable<Object> {
  private static final TRACER = GlobalOpenTelemetry.getTracer("test")

  final latch = new CountDownLatch(1)
  boolean startSpan

  @Override
  void run() {
    if (startSpan) {
      TRACER.spanBuilder("asyncChild").startSpan().end()
    }
    latch.countDown()
  }

  @Override
  Object call() throws Exception {
    run()
    return null
  }

  void waitForCompletion() throws InterruptedException {
    latch.await()
  }
}
