/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;

public class JvmExecutorMetricsAssertions {

  private static final AttributeKey<String> EXECUTOR_NAME_KEY = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME_KEY =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE_KEY = stringKey("jvm.executor.type");
  private static final AttributeKey<String> EXECUTOR_STATE_KEY = stringKey("jvm.executor.state");

  public static JvmExecutorMetricsAssertions create(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String ownerName,
      String executorType) {
    return new JvmExecutorMetricsAssertions(
        testing, instrumentationName, executorName, ownerName, executorType);
  }

  private final InstrumentationExtension testing;
  private final String instrumentationName;
  private final String executorName;
  private final String ownerName;
  private final String executorType;

  @Nullable private Long expectedActiveThreads;
  @Nullable private Long expectedIdleThreads;
  @Nullable private Long expectedCoreThreads;
  @Nullable private Long expectedMaxThreads;
  @Nullable private Long expectedQueueSize;
  @Nullable private Long expectedQueueRemaining;
  @Nullable private Long expectedCompletedTasks;
  @Nullable private Long expectedRejectedTasks;

  JvmExecutorMetricsAssertions(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String ownerName,
      String executorType) {
    this.testing = testing;
    this.instrumentationName = instrumentationName;
    this.executorName = executorName;
    this.ownerName = ownerName;
    this.executorType = executorType;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withActiveThreads(long value) {
    expectedActiveThreads = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withIdleThreads(long value) {
    expectedIdleThreads = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withCoreThreads(long value) {
    expectedCoreThreads = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withMaxThreads(long value) {
    expectedMaxThreads = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withQueueSize(long value) {
    expectedQueueSize = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withQueueRemaining(long value) {
    expectedQueueRemaining = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withCompletedTasks(long value) {
    expectedCompletedTasks = value;
    return this;
  }

  @CanIgnoreReturnValue
  public JvmExecutorMetricsAssertions withRejectedTasks(long value) {
    expectedRejectedTasks = value;
    return this;
  }

  public void assertExecutorEmitsMetrics() {
    if (expectedActiveThreads == null
        && expectedIdleThreads == null
        && expectedCoreThreads == null
        && expectedMaxThreads == null
        && expectedQueueSize == null
        && expectedQueueRemaining == null
        && expectedCompletedTasks == null
        && expectedRejectedTasks == null) {
      throw new IllegalStateException("At least one expected executor metric value must be set.");
    }

    if (expectedActiveThreads != null || expectedIdleThreads != null) {
      verifyThreadCount();
    }
    if (expectedCoreThreads != null) {
      verifyCoreThreads(expectedCoreThreads);
    }
    if (expectedMaxThreads != null) {
      verifyMaxThreads(expectedMaxThreads);
    }
    if (expectedQueueSize != null) {
      verifyQueueSize(expectedQueueSize);
    }
    if (expectedQueueRemaining != null) {
      verifyQueueRemaining(expectedQueueRemaining);
    }
    if (expectedCompletedTasks != null) {
      verifyCompletedTasks(expectedCompletedTasks);
    }
    if (expectedRejectedTasks != null) {
      verifyRejectedTasks(expectedRejectedTasks);
    }
  }

  private void verifyThreadCount() {
    List<Consumer<LongPointAssert>> pointAssertions = new ArrayList<>(2);
    Long activeThreads = expectedActiveThreads;
    if (activeThreads != null) {
      pointAssertions.add(point -> verifyThreadCountPoint(point, "active", activeThreads));
    }
    Long idleThreads = expectedIdleThreads;
    if (idleThreads != null) {
      pointAssertions.add(point -> verifyThreadCountPoint(point, "idle", idleThreads));
    }

    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.thread.count",
        metrics -> metrics.anySatisfy(metric -> verifyThreadCountMetric(metric, pointAssertions)));
  }

  private static void verifyThreadCountMetric(
      MetricData metric, List<Consumer<LongPointAssert>> pointAssertions) {
    assertThat(metric)
        .hasUnit("{thread}")
        .hasDescription("The number of executor threads that are currently in the described state.")
        .hasLongSumSatisfying(
            sum -> sum.isNotMonotonic().containsPointsSatisfying(pointAssertions));
  }

  private void verifyThreadCountPoint(LongPointAssert point, String state, long expectedValue) {
    point
        .hasAttributes(
            Attributes.of(
                EXECUTOR_NAME_KEY,
                executorName,
                EXECUTOR_OWNER_NAME_KEY,
                ownerName,
                EXECUTOR_TYPE_KEY,
                executorType,
                EXECUTOR_STATE_KEY,
                state))
        .hasValue(expectedValue);
  }

  private void verifyCoreThreads(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.thread.core",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{thread}",
                        "The core number of threads configured for the executor.",
                        false,
                        expectedValue)));
  }

  private void verifyMaxThreads(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.thread.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{thread}",
                        "The maximum number of threads allowed for the executor.",
                        false,
                        expectedValue)));
  }

  private void verifyQueueSize(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.queue.size",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{task}",
                        "The number of tasks currently queued for execution.",
                        false,
                        expectedValue)));
  }

  private void verifyQueueRemaining(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.queue.remaining",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{task}",
                        "The remaining task capacity of the executor queue.",
                        false,
                        expectedValue)));
  }

  private void verifyCompletedTasks(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.task.completed",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{task}",
                        "The number of tasks completed by the executor.",
                        true,
                        expectedValue)));
  }

  private void verifyRejectedTasks(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.task.rejected",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{task}",
                        "The number of tasks rejected by the executor.",
                        true,
                        expectedValue)));
  }

  private void verifyExecutorMetric(
      MetricData metric, String unit, String description, boolean monotonic, long expectedValue) {
    assertThat(metric)
        .hasUnit(unit)
        .hasDescription(description)
        .hasLongSumSatisfying(sum -> verifyExecutorAttributes(sum, monotonic, expectedValue));
  }

  private void verifyExecutorAttributes(LongSumAssert sum, boolean monotonic, long expectedValue) {
    if (monotonic) {
      sum.isMonotonic();
    } else {
      sum.isNotMonotonic();
    }
    sum.containsPointsSatisfying(
        point ->
            point
                .hasAttributes(
                    Attributes.of(
                        EXECUTOR_NAME_KEY,
                        executorName,
                        EXECUTOR_OWNER_NAME_KEY,
                        ownerName,
                        EXECUTOR_TYPE_KEY,
                        executorType))
                .hasValue(expectedValue));
  }
}
