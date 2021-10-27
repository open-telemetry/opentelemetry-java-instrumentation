/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.apache.tomcat.util.threads.TaskQueue
import org.apache.tomcat.util.threads.ThreadPoolExecutor

class ThreadPoolExecutorTest extends AgentInstrumentationSpecification {

  // Test that PropagatedContext isn't cleared when ThreadPoolExecutor.execute fails with
  // RejectedExecutionException
  def "test tomcat thread pool"() {
    setup:
    def reject = new AtomicBoolean()
    def queue = new TaskQueue() {
      @Override
      boolean offer(Runnable o) {
        // TaskQueue.offer returns false when parent.getPoolSize() < parent.getMaximumPoolSize()
        // here we simulate the same condition to trigger RejectedExecutionException handling in
        // tomcat ThreadPoolExecutor
        if (reject.get()) {
          reject.set(false)
          return false
        }
        return super.offer(o)
      }
    }
    def pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue)
    queue.setParent(pool)

    CountDownLatch latch = new CountDownLatch(1)

    runWithSpan("parent") {
      pool.execute(new Runnable() {
        @Override
        void run() {
          runWithSpan("child1") {
            latch.await()
          }
        }
      })

      reject.set(true)
      pool.execute(new Runnable() {
        @Override
        void run() {
          runWithSpan("child2") {
            latch.await()
          }
        }
      })
    }

    latch.countDown()

    expect:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "parent"
          kind SpanKind.INTERNAL
          hasNoParent()
        }
        span(1) {
          name "child1"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        span(2) {
          name "child2"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
      }
    }

    cleanup:
    pool.shutdown()
    pool.awaitTermination(10, TimeUnit.SECONDS)
  }
}
