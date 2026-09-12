/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static io.opentelemetry.javaagent.instrumentation.executors.metrics.JvmExecutorMetricsAssertions.assertNoExecutorMetrics;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPerTaskExecutorMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors-metrics";
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
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, EXECUTOR_NAME);

      Future<?> future = submitBlockingTask(executor, started, release);

      assertThat(started.await(10, SECONDS)).isTrue();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, EXECUTOR_NAME, EXECUTOR_TYPE)
          .withActiveThreads(1)
          .assertExecutorEmitsMetrics();

      release.countDown();
      future.get(10, SECONDS);
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, EXECUTOR_NAME, EXECUTOR_TYPE)
          .withActiveThreads(0)
          .assertExecutorEmitsMetrics();
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, EXECUTOR_NAME);
  }

  @Test
  void retainsInitialNameWhenLaterThreadsHaveDifferentNames() throws Exception {
    AtomicInteger sequence = new AtomicInteger();
    ThreadFactory threadFactory =
        runnable -> {
          int threadNumber = sequence.incrementAndGet();
          String namePrefix = threadNumber == 1 ? "initial-thread" : "later-thread";
          return new Thread(runnable, namePrefix + "-" + threadNumber);
        };
    ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch secondStarted = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
      Future<?> firstFuture = submitBlockingTask(executor, firstStarted, release);

      assertThat(firstStarted.await(10, SECONDS)).isTrue();
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "initial-thread-*", EXECUTOR_TYPE)
          .withActiveThreads(1)
          .assertExecutorEmitsMetrics();
      testing.clearData();

      Future<?> secondFuture = submitBlockingTask(executor, secondStarted, release);

      assertThat(secondStarted.await(10, SECONDS)).isTrue();
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "initial-thread-*", EXECUTOR_TYPE)
          .withActiveThreads(1)
          .assertExecutorEmitsMetrics();
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "later-thread-*");

      release.countDown();
      firstFuture.get(10, SECONDS);
      secondFuture.get(10, SECONDS);
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void reregistersOwnerAndNormalizationBeforeFirstWorker() throws Exception {
    NamedThreadFactory threadFactory = new NamedThreadFactory("thread-per-task-42-worker");
    ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);

    try {
      assertThat(threadFactory.createdThreadCount()).isZero();
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "thread-per-task-*-worker-*");

      reregister(executor, "tomcat", "all");
      executor.submit(() -> {}).get(10, SECONDS);
      testing.clearData();

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "thread-per-task-*-worker-*", "tomcat", EXECUTOR_TYPE)
          .withActiveThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void unregistersOnClose() throws Exception {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
      Future<?> future = submitBlockingTask(executor, started, release);

      assertThat(started.await(10, SECONDS)).isTrue();
      JvmExecutorMetricsAssertions.create(testing, INSTRUMENTATION_NAME, "unknown", EXECUTOR_TYPE)
          .withActiveThreads(1)
          .assertExecutorEmitsMetrics();

      release.countDown();
      future.get(10, SECONDS);
      executor.close();
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "unknown");
  }

  @Test
  void doesNotRecordMetricsWhenUnclosedExecutorIsCollected() throws Exception {
    WeakReference<ExecutorService> executorRef = createCollectableThreadPerTaskExecutor();

    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "collected-thread-per-task-*");
  }

  private static Future<?> submitBlockingTask(
      ExecutorService executor, CountDownLatch started, CountDownLatch release) {
    return executor.submit(
        () -> {
          started.countDown();
          try {
            release.await(10, SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
        });
  }

  private static void reregister(
      Executor executor, String ownerName, String threadNameNormalization) throws Exception {
    Class<?> executorMetrics =
        Class.forName(
            "io.opentelemetry.javaagent.bootstrap.executors.metrics.JdkExecutorMetrics",
            false,
            null);
    executorMetrics
        .getMethod("reregister", Executor.class, String.class, String.class)
        .invoke(null, executor, ownerName, threadNameNormalization);
  }

  private static WeakReference<ExecutorService> createCollectableThreadPerTaskExecutor()
      throws Exception {
    ExecutorService executor =
        Executors.newThreadPerTaskExecutor(new NamedThreadFactory("collected-thread-per-task"));

    executor.submit(() -> {}).get(10, SECONDS);
    JvmExecutorMetricsAssertions.create(
            testing, INSTRUMENTATION_NAME, "collected-thread-per-task-*", EXECUTOR_TYPE)
        .withActiveThreads(0)
        .assertExecutorEmitsMetrics();

    return new WeakReference<>(executor);
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
