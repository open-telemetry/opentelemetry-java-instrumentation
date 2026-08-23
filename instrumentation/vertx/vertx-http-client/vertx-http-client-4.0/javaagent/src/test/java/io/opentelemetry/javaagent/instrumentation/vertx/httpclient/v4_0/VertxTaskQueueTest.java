/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpclient.v4_0;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.vertx.core.impl.TaskQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxTaskQueueTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void testContextPropagation() throws InterruptedException {
    TaskQueue taskQueue = new TaskQueue();
    ExecutorService executor1 = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor1::shutdown);
    ExecutorService executor2 = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(executor2::shutdown);
    CountDownLatch start1 = new CountDownLatch(1);
    CountDownLatch start2 = new CountDownLatch(1);
    // start the first task
    testing.runWithSpan(
        "parent1",
        () ->
            taskQueue.execute(
                () -> {
                  // signal that the first task has started
                  start1.countDown();
                  // wait for the second task to be submitted
                  try {
                    start2.await(10, SECONDS);
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  testing.runWithSpan("span1", () -> {});
                },
                executor1));
    // wait for the first task to start
    start1.await(10, SECONDS);
    // start the second task
    testing.runWithSpan(
        "parent2",
        () -> taskQueue.execute(() -> testing.runWithSpan("span2", () -> {}), executor2));
    // signal that the second task has been submitted
    start2.countDown();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent1").hasNoParent(),
                span -> span.hasName("span1").hasParent(trace.getSpan(0))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent2").hasNoParent(),
                span -> span.hasName("span2").hasParent(trace.getSpan(0))));
  }
}
