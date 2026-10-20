/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.v2_2;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MessagingSystemIncubatingValues.AWS_SQS;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.function.Consumer;

public class AwsLambdaSqsMetricsAssertions {

  public static void assertMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      long processCount,
      long consumedMessageCount,
      String errorType) {
    assertMetrics(
        testing,
        instrumentationName,
        destination,
        processCount,
        consumedMessageCount,
        errorType,
        0,
        null);
  }

  public static void assertMetrics(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      long processCount,
      long consumedMessageCount,
      String errorType,
      long failedProcessCount,
      String failedProcessErrorType) {
    testing.waitAndAssertMetricsExactly(
        instrumentationName,
        processingDurationAssertion(
            destination, processCount, errorType, failedProcessCount, failedProcessErrorType),
        longSum(
            metric ->
                metric
                    .hasName("messaging.client.consumed.messages")
                    .hasUnit("{message}")
                    .hasDescription("Number of messages that were delivered to the application."),
            point ->
                point
                    .hasValue(consumedMessageCount)
                    .hasAttributesSatisfyingExactly(
                        equalTo(MESSAGING_OPERATION_NAME, "process"),
                        equalTo(MESSAGING_SYSTEM, AWS_SQS),
                        equalTo(MESSAGING_DESTINATION_NAME, destination),
                        equalTo(ERROR_TYPE, errorType))));
  }

  private static Consumer<MetricAssert> processingDurationAssertion(
      String destination,
      long processCount,
      String errorType,
      long failedProcessCount,
      String failedProcessErrorType) {
    Consumer<MetricAssert> metricAssertion =
        metric ->
            metric
                .hasName("messaging.process.duration")
                .hasUnit("s")
                .hasDescription("Duration of processing operation.");
    Consumer<HistogramPointAssert> processPoint =
        processPointAssertion(processCount, destination, errorType);

    if (failedProcessCount == 0) {
      return histogram(metricAssertion, processPoint);
    }
    return histogram(
        metricAssertion,
        processPoint,
        processPointAssertion(failedProcessCount, destination, failedProcessErrorType));
  }

  private static Consumer<HistogramPointAssert> processPointAssertion(
      long processCount, String destination, String errorType) {
    return point ->
        point
            .satisfies(data -> assertThat(data.getCount()).isEqualTo(processCount))
            .hasSumGreaterThan(0.0)
            .hasAttributesSatisfyingExactly(
                equalTo(MESSAGING_OPERATION_NAME, "process"),
                equalTo(MESSAGING_SYSTEM, AWS_SQS),
                equalTo(MESSAGING_DESTINATION_NAME, destination),
                equalTo(ERROR_TYPE, errorType));
  }

  private AwsLambdaSqsMetricsAssertions() {}
}
