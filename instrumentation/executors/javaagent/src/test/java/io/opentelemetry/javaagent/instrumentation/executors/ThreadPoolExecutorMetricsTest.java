/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.executors.ThreadPoolExecutorMetrics;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPoolExecutorMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors";
  private static final String DEFAULT_OWNER_NAME = "unknown";
  private static final String THREAD_POOL_EXECUTOR_TYPE = ThreadPoolExecutor.class.getName();
  private static final String EXPECTED_THREAD_NAME_NORMALIZATION =
      "test.name-normalization.expected";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void requiresExpectedMetricValue() {
    assertThatThrownBy(
            () ->
                JvmExecutorMetricsAssertions.create(
                        testing, INSTRUMENTATION_NAME, "executor", "owner", "type")
                    .assertExecutorEmitsMetrics())
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void recordsThreadPoolMetricsAndUnregistersOnShutdown() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("metrics-pool"));

    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
      executor.execute(
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

      executor.execute(() -> {});
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "metrics-pool-*",
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withActiveThreads(1)
          .withIdleThreads(0)
          .withCoreThreads(1)
          .withMaxThreads(1)
          .withQueueSize(1)
          .withQueueRemaining(0)
          .withCompletedTasks(0)
          .withRejectedTasks(1)
          .assertExecutorEmitsMetrics();
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertNoExecutorMetrics("metrics-pool-*", DEFAULT_OWNER_NAME);
  }

  @Test
  void skipsScheduledThreadPoolExecutor() {
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("scheduled-pool"));

    try {
      assertNoExecutorMetrics("scheduled-pool-*", DEFAULT_OWNER_NAME);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void retainsMetricsWhenOverriddenShutdownDoesNotShutdownExecutor() {
    UnlistedThreadPoolExecutor executor =
        new UnlistedThreadPoolExecutor(new NamedThreadFactory("unlisted-pool"));

    try {
      assertThat(executor.prestartCoreThread()).isTrue();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "unlisted-pool-*",
              DEFAULT_OWNER_NAME,
              UnlistedThreadPoolExecutor.class.getName())
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();

      executor.shutdown();

      assertThat(executor.isShutdown()).isFalse();
      testing.clearData();

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "unlisted-pool-*",
              DEFAULT_OWNER_NAME,
              UnlistedThreadPoolExecutor.class.getName())
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void doesNotRegisterMetricsWhenConstructorFails() {
    NamedThreadFactory threadFactory = new NamedThreadFactory("failed-pool");

    assertThatThrownBy(
            () ->
                new ThreadPoolExecutor(
                    2, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(1), threadFactory))
        .isInstanceOf(IllegalArgumentException.class);

    assertThat(threadFactory.createdThreadCount()).isZero();
    assertNoExecutorMetrics("failed-pool-*", DEFAULT_OWNER_NAME);
  }

  @Test
  void exportsMetricsOnlyAfterWorkerStarts() throws Exception {
    NamedThreadFactory threadFactory = new NamedThreadFactory("started-pool");
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(1, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(1), threadFactory);

    try {
      assertThat(threadFactory.createdThreadCount()).isZero();
      assertNoExecutorMetrics("started-pool-*", DEFAULT_OWNER_NAME);

      assertThat(executor.prestartCoreThread()).isTrue();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "started-pool-*",
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void doesNotReregisterAfterUnregister() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            1,
            SECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("reregister-pool"));

    try {
      executor.prestartCoreThread();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "reregister-pool-*",
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(1)
          .assertExecutorEmitsMetrics();

      ThreadPoolExecutorMetrics.unregister(executor);
      ThreadPoolExecutorMetrics.reregister(executor, "tomcat", "trailing");

      assertNoExecutorMetrics("reregister-pool-*", DEFAULT_OWNER_NAME);
      assertNoExecutorMetrics("reregister-pool-*", "tomcat");
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void doesNotRecordMetricsWhenUnclosedExecutorIsCollected() throws Exception {
    WeakReference<ThreadPoolExecutor> executorRef =
        new WeakReference<>(
            new ThreadPoolExecutor(
                0,
                1,
                1,
                SECONDS,
                new ArrayBlockingQueue<>(1),
                new NamedThreadFactory("collected-pool")));

    assertNoExecutorMetrics("collected-pool-*", DEFAULT_OWNER_NAME);
    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    assertNoExecutorMetrics("collected-pool-*", DEFAULT_OWNER_NAME);
  }

  @Test
  void normalizesExecutorThreadName() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("name-normalization-1-test"));

    try {
      String expectedExecutorName =
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "all"))
              ? "name-normalization-1-test-*"
              : "name-normalization-*-test-*";

      assertThat(executor.prestartCoreThread()).isTrue();

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              expectedExecutorName,
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(1)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void reregistersOwnerAndThreadFactory() throws Exception {
    NamedThreadFactory originalThreadFactory = new NamedThreadFactory("original-pool-42-worker");
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0, 2, 0, MILLISECONDS, new SynchronousQueue<>(), originalThreadFactory);
    CountDownLatch originalWorkerStarted = new CountDownLatch(1);
    CountDownLatch releaseOriginalWorker = new CountDownLatch(1);

    try {
      String originalExecutorName =
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "all"))
              ? "original-pool-42-worker-*"
              : "original-pool-*-worker-*";

      executor.execute(
          () -> {
            originalWorkerStarted.countDown();
            try {
              releaseOriginalWorker.await(10, SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new AssertionError(e);
            }
          });
      assertThat(originalWorkerStarted.await(10, SECONDS)).isTrue();

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              originalExecutorName,
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(originalThreadFactory.createdThreadCount()).isEqualTo(1);

      ThreadPoolExecutorMetrics.reregister(executor, "tomcat", "trailing");

      NamedThreadFactory replacementThreadFactory =
          new NamedThreadFactory("original-pool-43-worker");
      executor.setThreadFactory(replacementThreadFactory);
      assertThat(replacementThreadFactory.createdThreadCount()).isZero();

      testing.clearData();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "original-pool-42-worker-*",
              "tomcat",
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();

      CountDownLatch replacementWorkerStarted = new CountDownLatch(1);
      executor.execute(replacementWorkerStarted::countDown);
      assertThat(replacementWorkerStarted.await(10, SECONDS)).isTrue();
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);

      testing.clearData();

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "original-pool-43-worker-*",
              "tomcat",
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);
    } finally {
      releaseOriginalWorker.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void recordsMetricsWhenExecutorNamesCollide() throws Exception {
    ThreadPoolExecutor first =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("shared-pool"));
    ThreadPoolExecutor second =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("shared-pool"));

    try {
      assertThat(first.prestartCoreThread()).isTrue();
      assertThat(second.prestartCoreThread()).isTrue();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "shared-pool-*",
              DEFAULT_OWNER_NAME,
              THREAD_POOL_EXECUTOR_TYPE)
          .withActiveThreads(0)
          .withIdleThreads(0)
          .withMaxThreads(2)
          .withCoreThreads(2)
          .withQueueSize(0)
          .assertExecutorEmitsMetrics();
    } finally {
      first.shutdown();
      second.shutdown();
      assertThat(first.awaitTermination(10, SECONDS)).isTrue();
      assertThat(second.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private static void assertNoExecutorMetrics(String executorName, String ownerName) {
    testing.clearData();
    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .filteredOn(
                        metric ->
                            metric
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .allSatisfy(
                        metric ->
                            assertThat(metric.getLongSumData().getPoints())
                                .noneMatch(
                                    point ->
                                        executorName.equals(
                                                point
                                                    .getAttributes()
                                                    .get(stringKey("jvm.executor.name")))
                                            && ownerName.equals(
                                                point
                                                    .getAttributes()
                                                    .get(stringKey("jvm.executor.owner.name"))))));
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

  private static class UnlistedThreadPoolExecutor extends ThreadPoolExecutor {

    private UnlistedThreadPoolExecutor(ThreadFactory threadFactory) {
      super(1, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(1), threadFactory);
    }

    @Override
    public void shutdown() {}
  }
}
