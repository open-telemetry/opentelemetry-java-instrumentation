/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.javaagent.instrumentation.executors.metrics.JvmExecutorMetricsAssertions.assertNoExecutorMetric;
import static io.opentelemetry.javaagent.instrumentation.executors.metrics.JvmExecutorMetricsAssertions.assertNoExecutorMetrics;
import static io.opentelemetry.javaagent.instrumentation.executors.metrics.JvmExecutorMetricsAssertions.assertNoExecutorMetricsWithOwner;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.executors.metrics.ExecutorMetrics;
import io.opentelemetry.javaagent.instrumentation.executors.metrics.JvmExecutorMetricsAssertions;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPoolExecutorMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors-metrics";
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
                        testing, INSTRUMENTATION_NAME, "executor", "type")
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
              testing, INSTRUMENTATION_NAME, "metrics-pool-*", THREAD_POOL_EXECUTOR_TYPE)
          .withActiveThreads(1)
          .withIdleThreads(0)
          .withCoreThreads(1)
          .withMaxThreads(1)
          .withQueueSize(1)
          .withQueueCapacity(1)
          .withCompletedTasks(0)
          .withRejectedTasks(1)
          .assertExecutorEmitsMetrics();

      testing.clearData();

      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "metrics-pool-*", THREAD_POOL_EXECUTOR_TYPE)
          .withRejectedTasks(1)
          .assertExecutorEmitsMetrics();
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "metrics-pool-*");
  }

  @Test
  void doesNotExportQueueCapacityForUnboundedQueue() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            1,
            1,
            0,
            MILLISECONDS,
            new LinkedBlockingQueue<>(),
            new NamedThreadFactory("unbounded-pool"));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
      executor.execute(
          () -> {
            started.countDown();
            awaitLatch(release);
          });
      assertThat(started.await(10, SECONDS)).isTrue();
      executor.execute(() -> {});

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "unbounded-pool-*", THREAD_POOL_EXECUTOR_TYPE)
          .withQueueSize(1)
          .assertExecutorEmitsMetrics();
      assertNoExecutorMetric(
          testing, INSTRUMENTATION_NAME, "jvm.executor.queue.capacity", "unbounded-pool-*");
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void skipsScheduledThreadPoolExecutor() {
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("scheduled-pool"));

    try {
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "scheduled-pool-*");
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

      executor.shutdown();

      assertThat(executor.isShutdown()).isFalse();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "unlisted-pool-*",
              UnlistedThreadPoolExecutor.class.getName())
          .withCoreThreads(1)
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
    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "failed-pool-*");
  }

  @Test
  void exportsMetricsOnlyAfterWorkerStarts() throws Exception {
    NamedThreadFactory threadFactory = new NamedThreadFactory("started-pool");
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(1, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(1), threadFactory);

    try {
      assertThat(threadFactory.createdThreadCount()).isZero();
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "started-pool-*");

      assertThat(executor.prestartCoreThread()).isTrue();
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "started-pool-*", THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(1)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void recordsRejectionBeforeFirstWorkerStarts() throws Exception {
    ThreadFactory nullFactory = runnable -> null;
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(0, 1, 25, MILLISECONDS, new ArrayBlockingQueue<>(1), nullFactory);

    CountDownLatch completed = new CountDownLatch(2);

    try {
      executor.execute(completed::countDown);
      assertThatThrownBy(() -> executor.execute(() -> {}))
          .isInstanceOf(RejectedExecutionException.class);

      executor.setThreadFactory(new NamedThreadFactory("pre-worker-rejection-pool"));
      executor.execute(completed::countDown);
      assertThat(completed.await(10, SECONDS)).isTrue();

      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "pre-worker-rejection-pool-*",
              THREAD_POOL_EXECUTOR_TYPE)
          .withRejectedTasks(1)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void doesNotRecordMetricsWhenUnclosedExecutorIsCollected() throws Exception {
    WeakReference<ThreadPoolExecutor> executorRef = createCollectableThreadPoolExecutor();

    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "collected-pool-*");
  }

  private static WeakReference<ThreadPoolExecutor> createCollectableThreadPoolExecutor()
      throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0,
            1,
            1,
            SECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("collected-pool"));

    executor.submit(() -> {}).get(10, SECONDS);
    JvmExecutorMetricsAssertions.create(
            testing, INSTRUMENTATION_NAME, "collected-pool-*", THREAD_POOL_EXECUTOR_TYPE)
        .withMaxThreads(1)
        .assertExecutorEmitsMetrics();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(() -> assertThat(executor.getPoolSize()).isZero());

    return new WeakReference<>(executor);
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
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "trailing"))
              ? "name-normalization-1-test-*"
              : "name-normalization-*-test-*";

      assertThat(executor.prestartCoreThread()).isTrue();

      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, expectedExecutorName, THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(1)
          .assertExecutorEmitsMetrics();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void reregistersOwnerAndUpdatesIdentityAfterThreadFactoryChange() throws Exception {
    NamedThreadFactory originalThreadFactory = new NamedThreadFactory("original-pool-42-worker");
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0, 2, 0, MILLISECONDS, new SynchronousQueue<>(), originalThreadFactory);
    CountDownLatch originalWorkerStarted = new CountDownLatch(1);
    CountDownLatch releaseOriginalWorker = new CountDownLatch(1);

    try {
      String originalExecutorName =
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "trailing"))
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
              testing, INSTRUMENTATION_NAME, originalExecutorName, THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(originalThreadFactory.createdThreadCount()).isEqualTo(1);

      ExecutorMetrics.reregister(executor, "tomcat", "trailing");

      testing.clearData();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "original-pool-42-worker-*",
              "tomcat",
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertNoExecutorMetricsWithOwner(testing, INSTRUMENTATION_NAME, originalExecutorName, null);

      NamedThreadFactory replacementThreadFactory =
          new NamedThreadFactory("replacement-pool-43-worker");
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

      String replacementExecutorName =
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "trailing"))
              ? "replacement-pool-43-worker-*"
              : "replacement-pool-*-worker-*";
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              replacementExecutorName,
              "tomcat",
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, originalExecutorName);

      ExecutorMetrics.reregister(executor, null, "trailing");

      testing.clearData();
      JvmExecutorMetricsAssertions.create(
              testing,
              INSTRUMENTATION_NAME,
              "replacement-pool-43-worker-*",
              THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertNoExecutorMetricsWithOwner(
          testing, INSTRUMENTATION_NAME, "replacement-pool-43-worker-*", "tomcat");
    } finally {
      releaseOriginalWorker.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void staleWorkerFromOldThreadFactoryDoesNotHideReplacementMetrics() throws Exception {
    CountDownLatch oldFactoryEntered = new CountDownLatch(1);
    CountDownLatch releaseOldFactory = new CountDownLatch(1);
    CountDownLatch initialWorkerStarted = new CountDownLatch(1);
    CountDownLatch releaseInitialWorker = new CountDownLatch(1);
    CountDownLatch staleWorkerStarted = new CountDownLatch(1);
    CountDownLatch releaseStaleWorker = new CountDownLatch(1);
    BlockingSecondThreadFactory originalThreadFactory =
        new BlockingSecondThreadFactory("race-factory-a", oldFactoryEntered, releaseOldFactory);
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0, 3, 0, MILLISECONDS, new SynchronousQueue<>(), originalThreadFactory);
    AtomicReference<Throwable> submitterFailure = new AtomicReference<>();
    Thread submitter =
        new Thread(
            () -> {
              try {
                executor.execute(
                    () -> {
                      staleWorkerStarted.countDown();
                      awaitLatch(releaseStaleWorker);
                    });
              } catch (Throwable t) {
                submitterFailure.set(t);
              }
            },
            "stale-worker-submitter");

    try {
      executor.execute(
          () -> {
            initialWorkerStarted.countDown();
            awaitLatch(releaseInitialWorker);
          });
      assertThat(initialWorkerStarted.await(10, SECONDS)).isTrue();

      submitter.start();
      assertThat(oldFactoryEntered.await(10, SECONDS)).isTrue();

      NamedThreadFactory replacementThreadFactory = new NamedThreadFactory("race-factory-b");
      executor.setThreadFactory(replacementThreadFactory);

      releaseOldFactory.countDown();
      assertThat(staleWorkerStarted.await(10, SECONDS)).isTrue();
      submitter.join(10_000);
      assertThat(submitter.isAlive()).isFalse();
      assertThat(submitterFailure.get()).isNull();

      CountDownLatch replacementWorkerStarted = new CountDownLatch(1);
      executor.execute(replacementWorkerStarted::countDown);
      assertThat(replacementWorkerStarted.await(10, SECONDS)).isTrue();
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);

      testing.clearData();
      JvmExecutorMetricsAssertions.create(
              testing, INSTRUMENTATION_NAME, "race-factory-b-*", THREAD_POOL_EXECUTOR_TYPE)
          .withCoreThreads(0)
          .assertExecutorEmitsMetrics();
      assertNoExecutorMetrics(testing, INSTRUMENTATION_NAME, "race-factory-a-*");
    } finally {
      releaseOldFactory.countDown();
      releaseInitialWorker.countDown();
      releaseStaleWorker.countDown();
      submitter.join(10_000);
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
      assertThat(submitter.isAlive()).isFalse();
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
              testing, INSTRUMENTATION_NAME, "shared-pool-*", THREAD_POOL_EXECUTOR_TYPE)
          .withActiveThreads(0)
          .withIdleThreads(2)
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

  private static void awaitLatch(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException ignored) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
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

  private static final class BlockingSecondThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final CountDownLatch secondThreadEntered;
    private final CountDownLatch releaseSecondThread;
    private final AtomicInteger sequence = new AtomicInteger();

    private BlockingSecondThreadFactory(
        String namePrefix, CountDownLatch secondThreadEntered, CountDownLatch releaseSecondThread) {
      this.namePrefix = namePrefix;
      this.secondThreadEntered = secondThreadEntered;
      this.releaseSecondThread = releaseSecondThread;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      int threadNumber = sequence.incrementAndGet();
      if (threadNumber == 2) {
        secondThreadEntered.countDown();
        awaitLatch(releaseSecondThread);
      }
      return new Thread(runnable, namePrefix + "-" + threadNumber);
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
