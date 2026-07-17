/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
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
  private static final String EXECUTOR_NAME = "thread-per-task-*";
  private static final String EXECUTOR_TYPE = "java.util.concurrent.ThreadPerTaskExecutor";

  private static final AttributeKey<String> EXECUTOR_NAME_KEY = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME_KEY =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE_KEY = stringKey("jvm.executor.type");
  private static final AttributeKey<String> EXECUTOR_STATE_KEY = stringKey("jvm.executor.state");

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void recordsActiveThreadCountAndUnregistersOnShutdown() throws Exception {
    ExecutorService executor =
        Executors.newThreadPerTaskExecutor(new NamedThreadFactory("thread-per-task"));
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);

    try {
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
      assertActiveThreadCount(1);
      assertNoUnsupportedMetrics();

      release.countDown();
      future.get(10, SECONDS);
      assertActiveThreadCount(0);
    } finally {
      release.countDown();
      executor.shutdown();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }

    testing.clearData();
    assertNoThreadPerTaskMetric("jvm.executor.thread.count");
  }

  @Test
  void unregistersOnClose() {
    ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    try {
      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          "jvm.executor.thread.count",
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric.getLongSumData().getPoints())
                          .anySatisfy(
                              point ->
                                  assertThat(point.getAttributes().get(EXECUTOR_TYPE_KEY))
                                      .isEqualTo(EXECUTOR_TYPE))));
      executor.close();
    } finally {
      executor.shutdown();
    }

    testing.clearData();
    assertNoThreadPerTaskMetric("jvm.executor.thread.count");
  }

  @Test
  void doesNotRecordMetricsWhenUnclosedExecutorIsCollected() throws Exception {
    WeakReference<ExecutorService> executorRef =
        new WeakReference<>(
            Executors.newThreadPerTaskExecutor(
                new NamedThreadFactory("collected-thread-per-task")));

    GcUtils.awaitGc(executorRef, Duration.ofSeconds(10));

    testing.clearData();
    assertNoThreadPerTaskMetric("jvm.executor.thread.count");
  }

  @Test
  void reregistersOwnerAndThreadNameNormalization() {
    NamedThreadFactory threadFactory = new NamedThreadFactory("thread-per-task-42-worker");
    ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);

    try {
      assertActiveThreadCount(0, "thread-per-task-*-worker-*", "unknown");
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);

      ThreadPoolExecutorMetrics.reregister(executor, "tomcat", "trailing");
      testing.clearData();

      assertActiveThreadCount(0, "thread-per-task-42-worker-*", "tomcat");
      assertThat(threadFactory.createdThreadCount()).isEqualTo(1);
    } finally {
      executor.shutdown();
    }
  }

  private static void assertActiveThreadCount(long expectedValue) {
    assertActiveThreadCount(expectedValue, EXECUTOR_NAME, "unknown");
  }

  private static void assertActiveThreadCount(
      long expectedValue, String executorName, String ownerName) {
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
                              assertThat(point.getAttributes().get(EXECUTOR_NAME_KEY))
                                  .isEqualTo(executorName);
                              assertThat(point.getAttributes().get(EXECUTOR_OWNER_NAME_KEY))
                                  .isEqualTo(ownerName);
                              assertThat(point.getAttributes().get(EXECUTOR_TYPE_KEY))
                                  .isEqualTo(EXECUTOR_TYPE);
                              assertThat(point.getAttributes().get(EXECUTOR_STATE_KEY))
                                  .isEqualTo("active");
                              assertThat(point.getValue()).isEqualTo(expectedValue);
                            })));
  }

  private static void assertNoUnsupportedMetrics() {
    assertNoThreadPerTaskMetric("jvm.executor.thread.core");
    assertNoThreadPerTaskMetric("jvm.executor.thread.max");
    assertNoThreadPerTaskMetric("jvm.executor.queue.size");
    assertNoThreadPerTaskMetric("jvm.executor.queue.remaining");
    assertNoThreadPerTaskMetric("jvm.executor.task.completed");
    assertNoThreadPerTaskMetric("jvm.executor.task.rejected");
  }

  private static void assertNoThreadPerTaskMetric(String metricName) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.allSatisfy(
                metric ->
                    assertThat(metric.getLongSumData().getPoints())
                        .noneMatch(
                            point ->
                                EXECUTOR_TYPE.equals(
                                    point.getAttributes().get(EXECUTOR_TYPE_KEY)))));
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
