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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.bootstrap.executors.ThreadPoolExecutorMetrics;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ThreadPoolExecutorMetricsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.executors";
  private static final String DEFAULT_OWNER_NAME = "unknown";
  private static final String EXPECTED_THREAD_NAME_NORMALIZATION =
      "test.name-normalization.expected";

  private static final AttributeKey<String> EXECUTOR_NAME = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE = stringKey("jvm.executor.type");
  private static final AttributeKey<String> EXECUTOR_STATE = stringKey("jvm.executor.state");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

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

      assertThreadCountMetric("metrics-pool-*", DEFAULT_OWNER_NAME, "active", 1);
      assertThreadCountMetric("metrics-pool-*", DEFAULT_OWNER_NAME, "idle", 0);
      assertExecutorMetric(
          "jvm.executor.thread.core",
          "{thread}",
          "The core number of threads configured for the executor.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          1);
      assertExecutorMetric(
          "jvm.executor.thread.max",
          "{thread}",
          "The maximum number of threads allowed for the executor.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          1);
      assertExecutorMetric(
          "jvm.executor.queue.size",
          "{task}",
          "The number of tasks currently queued for execution.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          1);
      assertExecutorMetric(
          "jvm.executor.queue.remaining",
          "{task}",
          "The remaining task capacity of the executor queue.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          0);
      assertExecutorMetric(
          "jvm.executor.task.completed",
          "{task}",
          "The number of tasks completed by the executor.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          0);
      assertExecutorMetric(
          "jvm.executor.task.rejected",
          "{task}",
          "The number of tasks rejected by the executor.",
          "metrics-pool-*",
          DEFAULT_OWNER_NAME,
          1);
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    testing.clearData();
    assertNoExecutorMetric("jvm.executor.thread.count", "metrics-pool-*");
  }

  @Test
  void skipsScheduledThreadPoolExecutor() {
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("scheduled-pool"));

    try {
      assertNoExecutorMetric("jvm.executor.thread.count", "scheduled-pool-*");
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void unregistersUnlistedThreadPoolExecutorOnOverriddenShutdown() {
    UnlistedThreadPoolExecutor executor =
        new UnlistedThreadPoolExecutor(new NamedThreadFactory("unlisted-pool"));

    try {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "jvm.executor.thread.core",
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric.getLongSumData().getPoints())
                          .anySatisfy(
                              point ->
                                  assertThat(point.getAttributes().get(EXECUTOR_TYPE))
                                      .isEqualTo(UnlistedThreadPoolExecutor.class.getName()))));

      executor.shutdown();
      testing.clearData();

      assertNoExecutorMetric(
          "jvm.executor.thread.core",
          "unlisted-pool-*",
          UnlistedThreadPoolExecutor.class.getName());
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
    assertNoExecutorMetric("jvm.executor.thread.count", "failed-pool-*");
  }

  @Test
  void getsThreadNameFromStartedThread() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0,
            1,
            1,
            SECONDS,
            new ArrayBlockingQueue<>(1),
            new StartedThreadFactory("started-pool"));

    try {
      assertExecutorMetric(
          "jvm.executor.thread.core",
          "{thread}",
          "The core number of threads configured for the executor.",
          "started-pool-*",
          DEFAULT_OWNER_NAME,
          0);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void doesNotReregisterAfterUnregister() throws Exception {
    ThreadPoolExecutor executor =
        new ThreadPoolExecutor(
            0,
            1,
            1,
            SECONDS,
            new ArrayBlockingQueue<>(1),
            new NamedThreadFactory("concurrent-reregister-pool"));
    BlockingThreadFactory threadFactory = new BlockingThreadFactory();
    Thread reregisterThread =
        new Thread(() -> ThreadPoolExecutorMetrics.reregister(executor, threadFactory));

    try {
      reregisterThread.start();
      assertThat(threadFactory.started.await(10, SECONDS)).isTrue();

      ThreadPoolExecutorMetrics.unregister(executor);
      threadFactory.release.countDown();
      reregisterThread.join(10_000);
      assertThat(reregisterThread.isAlive()).isFalse();

      testing.clearData();
      assertNoExecutorMetric("jvm.executor.thread.core", "reregister-pool");
    } finally {
      threadFactory.release.countDown();
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

    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    testing.clearData();
    assertNoExecutorMetric("jvm.executor.thread.core", "collected-pool-*");
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

      assertExecutorMetric(
          "jvm.executor.thread.core",
          "{thread}",
          "The core number of threads configured for the executor.",
          expectedExecutorName,
          DEFAULT_OWNER_NAME,
          1);
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
            1, 1, 0, MILLISECONDS, new ArrayBlockingQueue<>(1), originalThreadFactory);

    try {
      String originalExecutorName =
          "trailing".equals(System.getProperty(EXPECTED_THREAD_NAME_NORMALIZATION, "all"))
              ? "original-pool-42-worker-*"
              : "original-pool-*-worker-*";
      assertExecutorMetric(
          "jvm.executor.thread.core",
          "{thread}",
          "The core number of threads configured for the executor.",
          originalExecutorName,
          DEFAULT_OWNER_NAME,
          1);
      assertThat(originalThreadFactory.createdThreadCount()).isEqualTo(1);

      NamedThreadFactory replacementThreadFactory =
          new NamedThreadFactory("original-pool-43-worker");
      executor.setThreadFactory(replacementThreadFactory);
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);

      ThreadPoolExecutorMetrics.reregister(executor, "tomcat", "trailing");
      testing.clearData();

      assertExecutorMetricAttributes(
          "jvm.executor.thread.core",
          "{thread}",
          "The core number of threads configured for the executor.",
          "original-pool-43-worker-*",
          "tomcat");
      assertNoExecutorMetric("jvm.executor.thread.core", originalExecutorName);
      assertThat(replacementThreadFactory.createdThreadCount()).isEqualTo(1);
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  void doesNotAddSuffixWhenExecutorNamesCollide() throws Exception {
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
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "jvm.executor.thread.core",
          metrics ->
              metrics.anySatisfy(
                  metric -> {
                    long sharedExecutorCount =
                        metric.getLongSumData().getPoints().stream()
                            .filter(
                                point ->
                                    "shared-pool-*"
                                        .equals(point.getAttributes().get(EXECUTOR_NAME)))
                            .count();
                    assertThat(sharedExecutorCount).isGreaterThan(0);
                    assertThat(metric.getLongSumData().getPoints())
                        .noneMatch(
                            point ->
                                "shared-pool-*-2".equals(point.getAttributes().get(EXECUTOR_NAME)));
                  }));
    } finally {
      first.shutdown();
      second.shutdown();
      assertThat(first.awaitTermination(10, SECONDS)).isTrue();
      assertThat(second.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  private static void assertThreadCountMetric(
      String executorName, String ownerName, String state, long expectedValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "jvm.executor.thread.count",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getLongSumData().getPoints())
                        .anySatisfy(
                            point -> {
                              assertThat(metric.getUnit()).isEqualTo("{thread}");
                              assertThat(metric.getDescription())
                                  .isEqualTo(
                                      "The number of executor threads that are currently in the described state.");
                              assertExecutorAttributes(point, executorName, ownerName);
                              assertThat(point.getAttributes().get(EXECUTOR_STATE))
                                  .isEqualTo(state);
                              assertThat(point.getValue()).isEqualTo(expectedValue);
                            })));
  }

  private static void assertExecutorMetric(
      String metricName,
      String unit,
      String description,
      String executorName,
      String ownerName,
      long expectedValue) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getLongSumData().getPoints())
                        .anySatisfy(
                            point -> {
                              assertThat(metric.getUnit()).isEqualTo(unit);
                              assertThat(metric.getDescription()).isEqualTo(description);
                              assertExecutorAttributes(point, executorName, ownerName);
                              assertThat(point.getValue()).isEqualTo(expectedValue);
                            })));
  }

  private static void assertNoExecutorMetric(String metricName, String executorName) {
    assertNoExecutorMetric(metricName, executorName, ThreadPoolExecutor.class.getName());
  }

  private static void assertNoExecutorMetric(
      String metricName, String executorName, String executorType) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.allSatisfy(
                metric ->
                    assertThat(metric.getLongSumData().getPoints())
                        .noneMatch(
                            point ->
                                executorName.equals(point.getAttributes().get(EXECUTOR_NAME))
                                    && executorType.equals(
                                        point.getAttributes().get(EXECUTOR_TYPE)))));
  }

  private static void assertExecutorMetricAttributes(
      String metricName, String unit, String description, String executorName, String ownerName) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  assertThat(metric.getUnit()).isEqualTo(unit);
                  assertThat(metric.getDescription()).isEqualTo(description);
                  assertThat(metric.getLongSumData().getPoints())
                      .anySatisfy(
                          point -> assertExecutorAttributes(point, executorName, ownerName));
                }));
  }

  private static void assertExecutorAttributes(
      LongPointData point, String executorName, String ownerName) {
    assertThat(point.getAttributes().get(EXECUTOR_NAME)).isEqualTo(executorName);
    assertThat(point.getAttributes().get(EXECUTOR_OWNER_NAME)).isEqualTo(ownerName);
    assertThat(point.getAttributes().get(EXECUTOR_TYPE))
        .isEqualTo(ThreadPoolExecutor.class.getName());
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

  private static final class StartedThreadFactory implements ThreadFactory {
    private final String namePrefix;
    private final AtomicInteger sequence = new AtomicInteger();

    private StartedThreadFactory(String namePrefix) {
      this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
      Thread thread = new Thread(runnable, namePrefix + "-" + sequence.incrementAndGet());
      thread.start();
      return thread;
    }
  }

  private static final class BlockingThreadFactory implements ThreadFactory {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    public Thread newThread(Runnable runnable) {
      started.countDown();
      try {
        release.await();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
      return new Thread(runnable, "reregister-pool");
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
