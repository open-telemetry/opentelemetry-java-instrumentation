/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;

@SuppressWarnings("deprecation") // using deprecated semconv
final class SqsMetricsAssertions {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.aws-sdk-2.2";
  private static final double[] DURATION_BUCKETS = {
    0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
  };

  static void assertProducerMetrics(
      InstrumentationExtension testing, int serverPort, long operationCount, long messageCount) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, serverPort, operationCount, "send", "send");
    assertMessageCounter(
        testing, "messaging.client.sent.messages", "send", messageCount, serverPort);
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertReceiveAndProcessMetrics(
      InstrumentationExtension testing,
      int serverPort,
      long receiveOperationCount,
      long messageCount) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, serverPort, receiveOperationCount, "receive", "receive");
    assertMessageCounter(
        testing, "messaging.client.consumed.messages", "receive", messageCount, serverPort);
    assertProcessDuration(testing, serverPort, messageCount);
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing, int serverPort, long messageCount) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertMessageCounter(
        testing, "messaging.client.consumed.messages", "process", messageCount, serverPort);
    assertProcessDuration(testing, serverPort, messageCount);
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertSettleMetrics(
      InstrumentationExtension testing, int serverPort, long operationCount) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, serverPort, operationCount, "delete", "settle");
    // settling messages does not deliver anything to the application
    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .extracting(MetricData::getName)
        .doesNotContain("messaging.client.consumed.messages");
    assertNoDeprecatedMessagingMetrics(testing);
  }

  private static void assertClientOperationDuration(
      InstrumentationExtension testing,
      int serverPort,
      long operationCount,
      String operationName,
      String operationType) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("s")
                        .hasDescription(
                            "Duration of messaging operation initiated by a producer or consumer client.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .satisfies(
                                                data ->
                                                    assertThat(data.getCount())
                                                        .isEqualTo(operationCount))
                                            .hasSumGreaterThan(0)
                                            .hasBucketBoundaries(DURATION_BUCKETS)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, operationName),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(ERROR_TYPE, null),
                                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                                equalTo(MESSAGING_OPERATION_TYPE, operationType),
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(SERVER_PORT, serverPort))))));
  }

  private static void assertProcessDuration(
      InstrumentationExtension testing, int serverPort, long operationCount) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("s")
                        .hasDescription("Duration of processing operation.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .satisfies(
                                                data ->
                                                    assertThat(data.getCount())
                                                        .isEqualTo(operationCount))
                                            .hasSumGreaterThan(0)
                                            .hasBucketBoundaries(DURATION_BUCKETS)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(ERROR_TYPE, null),
                                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(SERVER_PORT, serverPort))))));
  }

  private static void assertMessageCounter(
      InstrumentationExtension testing,
      String metricName,
      String operationName,
      long messageCount,
      int serverPort) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription(
                            metricName.equals("messaging.client.sent.messages")
                                ? "Number of messages producer attempted to send to the broker."
                                : "Number of messages that were delivered to the application.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, operationName),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(ERROR_TYPE, null),
                                                equalTo(MESSAGING_DESTINATION_NAME, "testSdkSqs"),
                                                equalTo(SERVER_ADDRESS, "localhost"),
                                                equalTo(SERVER_PORT, serverPort))
                                            .hasValue(messageCount)))));
  }

  private static void assertNoMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().startsWith("messaging."))
        .isEmpty();
  }

  private static void assertNoDeprecatedMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .extracting(MetricData::getName)
        .doesNotContain(
            "messaging.publish.duration",
            "messaging.publish.messages",
            "messaging.receive.duration",
            "messaging.receive.messages");
  }

  private SqsMetricsAssertions() {}
}
