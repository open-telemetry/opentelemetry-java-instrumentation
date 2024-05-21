/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.v7_0;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPoolExecutorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  // Test that PropagatedContext isn't cleared when ThreadPoolExecutor.execute fails with
  // RejectedExecutionException
  @Test
  void testTomcatThreadPool() throws InterruptedException {
    AtomicBoolean reject = new AtomicBoolean();
    TaskQueue queue =
        new TaskQueue() {
          @Override
          public boolean offer(Runnable o) {
            // TaskQueue.offer returns false when parent.getPoolSize() < parent.getMaximumPoolSize()
            // here we simulate the same condition to trigger RejectedExecutionException handling in
            // tomcat ThreadPoolExecutor
            if (reject.get()) {
              reject.set(false);
              return false;
            }
            return super.offer(o);
          }
        };

    ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, queue);
    queue.setParent(pool);

    CountDownLatch latch = new CountDownLatch(1);

    testing.runWithSpan(
        "parent",
        () -> {
          pool.execute(
              () -> {
                try {
                  testing.runWithSpan("child1", () -> latch.await());
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });

          reject.set(true);
          pool.execute(
              () -> {
                try {
                  testing.runWithSpan("child2", () -> latch.await());
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              });
        });

    latch.countDown();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("child1").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("child2").hasKind(SpanKind.INTERNAL).hasParent(trace.getSpan(0))));

    pool.shutdown();
    pool.awaitTermination(10, TimeUnit.SECONDS);
  }
}
