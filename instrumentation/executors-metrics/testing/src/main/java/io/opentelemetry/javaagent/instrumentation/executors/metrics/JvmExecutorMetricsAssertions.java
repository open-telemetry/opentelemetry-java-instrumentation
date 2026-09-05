/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.executors.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class JvmExecutorMetricsAssertions {

  private static final AttributeKey<String> EXECUTOR_NAME_KEY = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME_KEY =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE_KEY = stringKey("jvm.executor.type");
  private static final AttributeKey<String> EXECUTOR_THREAD_STATE_KEY =
      stringKey("jvm.executor.thread.state");

  private final InstrumentationExtension testing;
  private final String instrumentationName;
  private final String executorName;
  private final String executorOwnerName;
  private final String executorType;

  private Long expectedActiveThreads;
  private Long expectedIdleThreads;
  private Long expectedCoreThreads;
  private Long expectedMaxThreads;
  private Long expectedQueueSize;
  private Long expectedQueueCapacity;
  private Long expectedCompletedTasks;
  private Long expectedRejectedTasks;

  public static JvmExecutorMetricsAssertions create(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String executorType) {
    return new JvmExecutorMetricsAssertions(
        testing, instrumentationName, executorName, null, executorType);
  }

  public static JvmExecutorMetricsAssertions create(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String ownerName,
      String executorType) {
    return new JvmExecutorMetricsAssertions(
        testing, instrumentationName, executorName, ownerName, executorType);
  }

  public static void assertNoExecutorMetrics(
      InstrumentationExtension testing, String instrumentationName, String executorName) {
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
            metric ->
                instrumentationName.equals(metric.getInstrumentationScopeInfo().getName())
                    && metric.getName().startsWith("jvm.executor."))
        .flatExtracting(metric -> metric.getLongSumData().getPoints())
        .noneMatch(point -> executorName.equals(point.getAttributes().get(EXECUTOR_NAME_KEY)));
  }

  public static void assertNoExecutorMetricsWithOwner(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String ownerName) {
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
            metric ->
                instrumentationName.equals(metric.getInstrumentationScopeInfo().getName())
                    && metric.getName().startsWith("jvm.executor."))
        .flatExtracting(metric -> metric.getLongSumData().getPoints())
        .noneMatch(
            point ->
                executorName.equals(point.getAttributes().get(EXECUTOR_NAME_KEY))
                    && Objects.equals(
                        ownerName, point.getAttributes().get(EXECUTOR_OWNER_NAME_KEY)));
  }

  public static void assertNoExecutorMetric(
      InstrumentationExtension testing,
      String instrumentationName,
      String metricName,
      String executorName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                instrumentationName.equals(metric.getInstrumentationScopeInfo().getName())
                    && metricName.equals(metric.getName()))
        .flatExtracting(metric -> metric.getLongSumData().getPoints())
        .noneMatch(point -> executorName.equals(point.getAttributes().get(EXECUTOR_NAME_KEY)));
  }

  JvmExecutorMetricsAssertions(
      InstrumentationExtension testing,
      String instrumentationName,
      String executorName,
      String executorOwnerName,
      String executorType) {
    this.testing = testing;
    this.instrumentationName = instrumentationName;
    this.executorName = executorName;
    this.executorOwnerName = executorOwnerName;
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
  public JvmExecutorMetricsAssertions withQueueCapacity(long value) {
    expectedQueueCapacity = value;
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
        && expectedQueueCapacity == null
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
    if (expectedQueueCapacity != null) {
      verifyQueueCapacity(expectedQueueCapacity);
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
        .hasDescription(
            "The number of executor threads that are currently in the state described by the jvm.executor.thread.state attribute.")
        .hasLongSumSatisfying(
            sum -> sum.isNotMonotonic().containsPointsSatisfying(pointAssertions));
  }

  private void verifyThreadCountPoint(LongPointAssert point, String state, long expectedValue) {
    List<AttributeAssertion> assertions = executorAttributeAssertions();
    assertions.add(equalTo(EXECUTOR_THREAD_STATE_KEY, state));
    point.hasAttributesSatisfyingExactly(assertions).hasValue(expectedValue);
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
                        "The number of core threads configured for the executor.",
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

  private void verifyQueueCapacity(long expectedValue) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "jvm.executor.queue.capacity",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    verifyExecutorMetric(
                        metric,
                        "{task}",
                        "The maximum number of tasks the executor queue can hold.",
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
                .hasAttributesSatisfyingExactly(executorAttributeAssertions())
                .hasValue(expectedValue));
  }

  private List<AttributeAssertion> executorAttributeAssertions() {
    List<AttributeAssertion> assertions = new ArrayList<>(3);
    assertions.add(equalTo(EXECUTOR_NAME_KEY, executorName));
    if (executorOwnerName != null) {
      assertions.add(equalTo(EXECUTOR_OWNER_NAME_KEY, executorOwnerName));
    }
    assertions.add(equalTo(EXECUTOR_TYPE_KEY, executorType));
    return assertions;
  }
}
