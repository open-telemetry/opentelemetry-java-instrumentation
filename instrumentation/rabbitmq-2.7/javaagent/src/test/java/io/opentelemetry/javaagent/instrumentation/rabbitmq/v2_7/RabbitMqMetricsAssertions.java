/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import org.assertj.core.api.AbstractLongAssert;
import org.assertj.core.api.AbstractStringAssert;

class RabbitMqMetricsAssertions {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.rabbitmq-2.7";

  static void assertProducerMetrics(
      InstrumentationExtension testing, String destination, String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, "publish", "send", destination, errorType);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.sent.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    assertThat(metric)
                        .hasUnit("{message}")
                        .hasDescription(
                            "Number of messages producer attempted to send to the broker.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "publish"),
                                                equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                equalTo(ERROR_TYPE, errorType),
                                                equalTo(MESSAGING_DESTINATION_NAME, destination),
                                                satisfies(
                                                    SERVER_ADDRESS,
                                                    AbstractStringAssert::isNotBlank),
                                                satisfies(
                                                    SERVER_PORT,
                                                    AbstractLongAssert::isPositive))))));
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertReceiveMetrics(
      InstrumentationExtension testing,
      String destination,
      String errorType,
      long consumedMessages) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, "receive", "receive", destination, errorType);
    if (consumedMessages == 0) {
      assertNoMetric(testing, "messaging.client.consumed.messages");
    } else {
      assertConsumedMessages(testing, "receive", destination, errorType, consumedMessages);
    }
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing,
      String destination,
      String errorType,
      long consumedMessages) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

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
                                histogram
                                    .satisfies(data -> assertThat(data.getPoints()).hasSize(1))
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(consumedMessages)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                    equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                    equalTo(ERROR_TYPE, errorType),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, destination),
                                                    satisfies(
                                                        SERVER_ADDRESS,
                                                        AbstractStringAssert::isNotBlank),
                                                    satisfies(
                                                        SERVER_PORT,
                                                        AbstractLongAssert::isPositive))))));
    assertConsumedMessages(testing, "process", destination, errorType, consumedMessages);
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertSettleMetrics(
      InstrumentationExtension testing, String operationName, String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertClientOperationDuration(testing, operationName, "settle", null, errorType);
    assertNoMetric(testing, "messaging.client.consumed.messages");
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertNoMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().startsWith("messaging."))
        .isEmpty();
  }

  private static void assertClientOperationDuration(
      InstrumentationExtension testing,
      String operationName,
      String operationType,
      String destination,
      String errorType) {
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
                                histogram
                                    .satisfies(data -> assertThat(data.getPoints()).hasSize(1))
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        MESSAGING_OPERATION_NAME, operationName),
                                                    equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                    equalTo(ERROR_TYPE, errorType),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, destination),
                                                    equalTo(
                                                        MESSAGING_OPERATION_TYPE, operationType),
                                                    satisfies(
                                                        SERVER_ADDRESS,
                                                        AbstractStringAssert::isNotBlank),
                                                    satisfies(
                                                        SERVER_PORT,
                                                        AbstractLongAssert::isPositive))))));
  }

  private static void assertConsumedMessages(
      InstrumentationExtension testing,
      String operationName,
      String destination,
      String errorType,
      long consumedMessages) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
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
                                sum.satisfies(data -> assertThat(data.getPoints()).hasSize(1))
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(consumedMessages)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(
                                                        MESSAGING_OPERATION_NAME, operationName),
                                                    equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                    equalTo(ERROR_TYPE, errorType),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, destination),
                                                    satisfies(
                                                        SERVER_ADDRESS,
                                                        AbstractStringAssert::isNotBlank),
                                                    satisfies(
                                                        SERVER_PORT,
                                                        AbstractLongAssert::isPositive))))));
  }

  private static void assertNoMetric(InstrumentationExtension testing, String metricName) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metric.getName().equals(metricName))
        .isEmpty();
  }

  private static void assertNoDeprecatedMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME))
        .extracting(MetricData::getName)
        .doesNotContain(
            "messaging.publish.duration",
            "messaging.receive.duration",
            "messaging.receive.messages");
  }

  private RabbitMqMetricsAssertions() {}
}
