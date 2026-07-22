/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.executors.ThreadPoolExecutorMetrics;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPerTaskExecutorMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors";
  private static final String DEFAULT_OWNER_NAME = "unknown";
  private static final String EXECUTOR_NAME = "thread-per-task-*";
  private static final String EXECUTOR_TYPE = "java.util.concurrent.ThreadPerTaskExecutor";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void recordsActiveThreadCountAndUnregistersOnShutdown() throws Exception {
    NamedThreadFactory threadFactory = new NamedThreadFactory("thread-per-task");
    ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
      assertThat(threadFactory.createdThreadCount()).isZero();
      assertNoThreadPerTaskMetrics(EXECUTOR_NAME, DEFAULT_OWNER_NAME);

      Future<?> future =
          executor.submit(
              () -> {
                started.countDown();
                try {
                  release.await(10, SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new AssertionError(e);
                }
              });

      assertThat(started.await(10, SECONDS)).isTrue();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, EXECUTOR_NAME, DEFAULT_OWNER_NAME, EXECUTOR_TYPE)
          .withActiveThreads(1)
          .assertExecutorEmitsMetrics();

      release.countDown();
      future.get(10, SECONDS);
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, EXECUTOR_NAME, DEFAULT_OWNER_NAME, EXECUTOR_TYPE)
          .withActiveThreads(0)
          .assertExecutorEmitsMetrics();
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertNoThreadPerTaskMetrics(EXECUTOR_NAME, DEFAULT_OWNER_NAME);
  }

  @Test
  void unregistersOnClose() throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    try {
      executor.submit(() -> {}).get(10, SECONDS);
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, DEFAULT_OWNER_NAME, DEFAULT_OWNER_NAME, EXECUTOR_TYPE)
          .withActiveThreads(0)
          .assertExecutorEmitsMetrics();
      executor.close();
    } finally {
      executor.shutdown();
    }

    assertNoThreadPerTaskMetrics(DEFAULT_OWNER_NAME, DEFAULT_OWNER_NAME);
  }

  @Test
  void doesNotRecordMetricsWhenUnclosedExecutorIsCollected() throws Exception {
    WeakReference<ExecutorService> executorRef =
        new WeakReference<>(
            Executors.newThreadPerTaskExecutor(
                new NamedThreadFactory("collected-thread-per-task")));

    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    assertNoThreadPerTaskMetrics("collected-thread-per-task-*", DEFAULT_OWNER_NAME);
  }

  @Test
  void reregistersOwnerAndThreadNameNormalization() throws Exception {
    NamedThreadFactory threadFactory = new NamedThreadFactory("thread-per-task-42-worker");
    ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);

    try {
      assertThat(threadFactory.createdThreadCount()).isZero();
      assertNoThreadPerTaskMetrics("thread-per-task-*-worker-*", DEFAULT_OWNER_NAME);

      ThreadPoolExecutorMetrics.reregister(executor, "tomcat", "trailing");
      executor.submit(() -> {}).get(10, SECONDS);
      testing.clearData();

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "thread-per-task-42-worker-*", "tomcat", EXECUTOR_TYPE)
          .withActiveThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  private static void assertNoThreadPerTaskMetrics(String executorName, String ownerName) {
    testing.clearData();
    testing
        .getOpenTelemetry()
        .getMeter("test")
        .counterBuilder("test.executor.metrics.collection")
        .build()
        .add(1);
    testing.waitAndAssertMetrics(
        "test", "test.executor.metrics.collection", metrics -> metrics.isNotEmpty());

    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .allSatisfy(
            metric ->
                assertThat(metric.getLongSumData().getPoints())
                    .noneMatch(
                        point ->
                            executorName.equals(
                                    point.getAttributes().get(stringKey("jvm.executor.name")))
                                && ownerName.equals(
                                    point
                                        .getAttributes()
                                        .get(stringKey("jvm.executor.owner.name")))));
  }

  private static final class NamedThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger();

    private NamedThreadFactory(String namePrefix) {
      this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      return new Thread(runnable, namePrefix + "-" + sequence.incrementAndGet());
    }

    private int createdThreadCount() {
      return sequence.get();
    }
  }
}
