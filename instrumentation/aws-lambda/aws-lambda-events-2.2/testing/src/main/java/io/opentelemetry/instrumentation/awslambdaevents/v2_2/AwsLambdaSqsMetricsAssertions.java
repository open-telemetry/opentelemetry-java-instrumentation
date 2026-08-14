/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;

public class AwsLambdaSqsMetricsAssertions {

  public static void assertMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      long processCount,
      long consumedMessageCount,
      String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertMetricNamesAbsent(
          testing,
          "messaging.process.duration",
          "messaging.client.consumed.messages",
          "messaging.publish.duration",
          "messaging.publish.messages",
          "messaging.receive.duration",
          "messaging.receive.messages");
      return;
    }

    testing.waitAndAssertMetrics(
        instrumentationName,
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
                                                        .isEqualTo(processCount))
                                            .hasSumGreaterThan(0.0)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(MESSAGING_DESTINATION_NAME, destination),
                                                equalTo(ERROR_TYPE, errorType))))));
    testing.waitAndAssertMetrics(
        instrumentationName,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription(
                            "Number of messages that were delivered to the application.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(consumedMessageCount)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                                                equalTo(MESSAGING_DESTINATION_NAME, destination),
                                                equalTo(ERROR_TYPE, errorType))))));
    assertMetricNamesAbsent(
        testing,
        "messaging.publish.duration",
        "messaging.publish.messages",
        "messaging.receive.duration",
        "messaging.receive.messages");
  }

  private static void assertMetricNamesAbsent(
      InstrumentationExtension testing, String... metricNames) {
    assertThat(testing.metrics()).extracting(MetricData::getName).doesNotContain(metricNames);
  }

  private AwsLambdaSqsMetricsAssertions() {}
}
