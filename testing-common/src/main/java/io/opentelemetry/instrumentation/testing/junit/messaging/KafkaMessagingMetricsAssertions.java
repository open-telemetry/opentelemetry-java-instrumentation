/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.messaging;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;

public final class KafkaMessagingMetricsAssertions {

  private static final String CLIENT_OPERATION_DURATION = "messaging.client.operation.duration";
  private static final String SENT_MESSAGES = "messaging.client.sent.messages";
  private static final String CONSUMED_MESSAGES = "messaging.client.consumed.messages";
  private static final String PROCESS_DURATION = "messaging.process.duration";

  public static void assertSendMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String partition,
      long count,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertDuration(
        testing,
        instrumentationName,
        CLIENT_OPERATION_DURATION,
        "Duration of messaging operation initiated by a producer or consumer client.",
        "send",
        "send",
        destination,
        null,
        partition,
        count,
        errorType);
    assertSentMessagesMetrics(
        testing, instrumentationName, destination, partition, count, errorType);
  }

  public static void assertSentMessagesMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String partition,
      long count,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertDeprecatedMetricsAbsent(testing);
    assertCounter(
        testing,
        instrumentationName,
        SENT_MESSAGES,
        "Number of messages producer attempted to send to the broker.",
        "send",
        destination,
        null,
        partition,
        count,
        errorType);
  }

  public static void assertReceiveMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String group,
      String partition,
      long operationCount,
      long messageCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertReceiveDurationMetrics(
        testing, instrumentationName, destination, group, partition, operationCount, errorType);
    assertCounter(
        testing,
        instrumentationName,
        CONSUMED_MESSAGES,
        "Number of messages that were delivered to the application.",
        "poll",
        destination,
        group,
        partition,
        messageCount,
        errorType);
    assertDeprecatedMetricsAbsent(testing);
  }

  public static void assertReceiveDurationMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String group,
      String partition,
      long operationCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertDuration(
        testing,
        instrumentationName,
        CLIENT_OPERATION_DURATION,
        "Duration of messaging operation initiated by a producer or consumer client.",
        "poll",
        "receive",
        destination,
        group,
        partition,
        operationCount,
        errorType);
    assertMetricAbsent(testing, instrumentationName, CONSUMED_MESSAGES);
    assertDeprecatedMetricsAbsent(testing);
  }

  /**
   * Asserts the process operation's duration metric, and that the process operation never records
   * the consumed messages count. That count is owned by the receive operation.
   */
  public static void assertProcessMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String group,
      String partition,
      long operationCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertProcessDurationMetrics(
        testing, instrumentationName, destination, group, partition, operationCount, errorType);
    assertNoProcessConsumedMessages(testing, instrumentationName);
  }

  /**
   * Asserts the process operation's duration metric, and that the process operation also records
   * the consumed messages count. That happens when no receive operation runs for the consumer, so
   * the process operation is the only operation that can report the delivery.
   */
  public static void assertProcessMetricsWithConsumedMessages(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String group,
      String partition,
      long operationCount,
      long messageCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertProcessDurationMetrics(
        testing, instrumentationName, destination, group, partition, operationCount, errorType);
    assertCounter(
        testing,
        instrumentationName,
        CONSUMED_MESSAGES,
        "Number of messages that were delivered to the application.",
        "process",
        destination,
        group,
        partition,
        messageCount,
        errorType);
  }

  public static void assertProcessDurationMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String group,
      String partition,
      long operationCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }

    assertDuration(
        testing,
        instrumentationName,
        PROCESS_DURATION,
        "Duration of processing operation.",
        "process",
        null,
        destination,
        group,
        partition,
        operationCount,
        errorType);
  }

  public static void assertNoNewMetrics(
      InstrumentationExtension testing, String instrumentationName) {
    assertMetricAbsent(testing, instrumentationName, CLIENT_OPERATION_DURATION);
    assertMetricAbsent(testing, instrumentationName, SENT_MESSAGES);
    assertMetricAbsent(testing, instrumentationName, CONSUMED_MESSAGES);
    assertMetricAbsent(testing, instrumentationName, PROCESS_DURATION);
    assertDeprecatedMetricsAbsent(testing);
  }

  public static void assertClientOperationDurationMetricAbsent(
      InstrumentationExtension testing, String instrumentationName) {
    assertMetricAbsent(testing, instrumentationName, CLIENT_OPERATION_DURATION);
  }

  public static void assertConsumedMessagesMetricAbsent(
      InstrumentationExtension testing, String instrumentationName) {
    assertMetricAbsent(testing, instrumentationName, CONSUMED_MESSAGES);
  }

  public static void assertProcessMetricPointCounts(
      InstrumentationExtension testing, String instrumentationName, int durationPointCount) {
    if (!emitStableMessagingSemconv()) {
      return;
    }
    testing.waitAndAssertMetrics(
        instrumentationName,
        PROCESS_DURATION,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric.getHistogramData().getPoints()).hasSize(durationPointCount)));
  }

  /**
   * Asserts that the given number of messages was consumed in total, summed over every attribute
   * combination. Since the consumed messages count must be reported exactly once per delivery, this
   * catches a delivery being counted by more than one operation.
   */
  public static void assertTotalConsumedMessages(
      InstrumentationExtension testing, String instrumentationName, long total) {
    if (!emitStableMessagingSemconv()) {
      assertNoNewMetrics(testing, instrumentationName);
      return;
    }
    testing.waitAndAssertMetrics(
        instrumentationName,
        CONSUMED_MESSAGES,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(
                            metric.getLongSumData().getPoints().stream()
                                .mapToLong(LongPointData::getValue)
                                .sum())
                        .isEqualTo(total)));
  }

  private static void assertDuration(
      InstrumentationExtension testing,
      String instrumentationName,
      String metricName,
      String description,
      String operation,
      String operationType,
      String destination,
      String group,
      String partition,
      long count,
      String errorType) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        metricName,
        metrics ->
            metrics
                .filteredOn(
                    metric ->
                        metric.getUnit().equals("s")
                            && metric.getDescription().equals(description)
                            && metric.getHistogramData().getPoints().stream()
                                .anyMatch(
                                    point ->
                                        point.getCount() == count
                                            && point.getSum() > 0.0
                                            && point
                                                .getAttributes()
                                                .asMap()
                                                .equals(
                                                    attributes(
                                                            operation,
                                                            operationType,
                                                            destination,
                                                            group,
                                                            partition,
                                                            errorType)
                                                        .asMap())))
                .isNotEmpty());
  }

  private static void assertCounter(
      InstrumentationExtension testing,
      String instrumentationName,
      String metricName,
      String description,
      String operation,
      String destination,
      String group,
      String partition,
      long count,
      String errorType) {
    testing.waitAndAssertMetrics(
        instrumentationName,
        metricName,
        metrics ->
            metrics
                .filteredOn(
                    metric ->
                        metric.getUnit().equals("{message}")
                            && metric.getDescription().equals(description)
                            && metric.getLongSumData().getPoints().stream()
                                .anyMatch(
                                    point ->
                                        point.getValue() == count
                                            && point
                                                .getAttributes()
                                                .asMap()
                                                .equals(
                                                    attributes(
                                                            operation,
                                                            null,
                                                            destination,
                                                            group,
                                                            partition,
                                                            errorType)
                                                        .asMap())))
                .isNotEmpty());
  }

  private static Attributes attributes(
      String operation,
      String operationType,
      String destination,
      String group,
      String partition,
      String errorType) {
    return Attributes.builder()
        .put(stringKey("messaging.operation.name"), operation)
        .put(stringKey("messaging.system"), "kafka")
        .put(stringKey("error.type"), errorType)
        .put(stringKey("messaging.consumer.group.name"), group)
        .put(stringKey("messaging.destination.name"), destination)
        .put(stringKey("messaging.operation.type"), operationType)
        .put(stringKey("messaging.destination.partition.id"), partition)
        .build();
  }

  private static void assertMetricAbsent(
      InstrumentationExtension testing, String instrumentationName, String metricName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(instrumentationName))
        .extracting(MetricData::getName)
        .doesNotContain(metricName);
  }

  private static void assertNoProcessConsumedMessages(
      InstrumentationExtension testing, String instrumentationName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(instrumentationName)
                    && metric.getName().equals(CONSUMED_MESSAGES))
        .flatExtracting(metric -> metric.getLongSumData().getPoints())
        .allSatisfy(
            point ->
                assertThat(point.getAttributes().get(stringKey("messaging.operation.name")))
                    .isNotEqualTo("process"));
  }

  private static void assertDeprecatedMetricsAbsent(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .extracting(MetricData::getName)
        .doesNotContain(
            "messaging.publish.duration",
            "messaging.receive.duration",
            "messaging.receive.messages");
  }

  private KafkaMessagingMetricsAssertions() {}
}
