/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.VirtualFieldInstalledMarker;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ThreadPoolExecutorTest {

  @Test
  void virtualFieldsAdded() {
    assertThat(VirtualFieldInstalledMarker.class).isAssignableFrom(FutureTask.class);
  }

  @Test
  void shouldPassOriginalRunnableToBeforeAfterMethods() throws InterruptedException {
    CountDownLatch latch = new CountDownLatch(1);
    Runnable task = latch::countDown;

    RunnableCheckingThreadPoolExecutor executor = new RunnableCheckingThreadPoolExecutor(task);

    Baggage baggage = Baggage.builder().put("test", "test").build();
    try (Scope ignored = baggage.makeCurrent()) {
      executor.execute(task);
    }
    latch.await(10, TimeUnit.SECONDS);

    assertThat(executor.sameTaskBefore).isTrue();
    await().untilAsserted(() -> assertThat(executor.sameTaskAfter).isTrue());
  }

  // class is configured to be instrumented via otel.instrumentation.executors.include
  static class RunnableCheckingThreadPoolExecutor extends ThreadPoolExecutor {

    final Runnable expectedTask;

    final AtomicBoolean sameTaskBefore = new AtomicBoolean();
    final AtomicBoolean sameTaskAfter = new AtomicBoolean();

    RunnableCheckingThreadPoolExecutor(Runnable expectedTask) {
      super(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
      this.expectedTask = expectedTask;
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
      sameTaskBefore.set(r == expectedTask);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
      sameTaskAfter.set(r == expectedTask);
    }
  }
}
