/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;

class SpringRabbitMetricsAssertions {

  private static final String RABBIT_INSTRUMENTATION_NAME = "io.opentelemetry.rabbitmq-2.7";
  private static final String SPRING_INSTRUMENTATION_NAME = "io.opentelemetry.spring-rabbit-1.0";

  static void assertProcessMetrics(
      InstrumentationExtension testing, String destination, String springErrorType) {
    assertProcessMetrics(testing, destination, springErrorType, 1);
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing,
      String destination,
      String springErrorType,
      long consumedMessagesCount) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertProcessDuration(testing, SPRING_INSTRUMENTATION_NAME, destination, springErrorType);
    testing.waitAndAssertMetrics(
        SPRING_INSTRUMENTATION_NAME,
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
                                                .hasValue(consumedMessagesCount)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                    equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                    equalTo(ERROR_TYPE, springErrorType),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, destination),
                                                    satisfies(
                                                        SERVER_ADDRESS, val -> val.isNotBlank()),
                                                    satisfies(
                                                        SERVER_PORT, val -> val.isPositive()))))));
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(RABBIT_INSTRUMENTATION_NAME)
                    && metric.getName().equals("messaging.process.duration"))
        .isEmpty();
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(RABBIT_INSTRUMENTATION_NAME)
                    && metric.getName().equals("messaging.client.consumed.messages"))
        .isEmpty();
    assertNoDeprecatedMessagingMetrics(testing);
  }

  static void assertRabbitProcessDuration(InstrumentationExtension testing, String destination) {
    if (!emitStableMessagingSemconv()) {
      assertNoMessagingMetrics(testing);
      return;
    }

    assertProcessDuration(testing, RABBIT_INSTRUMENTATION_NAME, destination, null);
  }

  private static void assertProcessDuration(
      InstrumentationExtension testing,
      String instrumentationName,
      String destination,
      String errorType) {
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
                                histogram
                                    .satisfies(data -> assertThat(data.getPoints()).hasSize(1))
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasCount(1)
                                                .hasAttributesSatisfyingExactly(
                                                    equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                    equalTo(MESSAGING_SYSTEM, "rabbitmq"),
                                                    equalTo(ERROR_TYPE, errorType),
                                                    equalTo(
                                                        MESSAGING_DESTINATION_NAME, destination),
                                                    satisfies(
                                                        SERVER_ADDRESS, val -> val.isNotBlank()),
                                                    satisfies(
                                                        SERVER_PORT, val -> val.isPositive()))))));
  }

  private static void assertNoMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                (metric.getInstrumentationScopeInfo().getName().equals(RABBIT_INSTRUMENTATION_NAME)
                        || metric
                            .getInstrumentationScopeInfo()
                            .getName()
                            .equals(SPRING_INSTRUMENTATION_NAME))
                    && metric.getName().startsWith("messaging."))
        .isEmpty();
  }

  private static void assertNoDeprecatedMessagingMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .filteredOn(
            metric ->
                metric.getInstrumentationScopeInfo().getName().equals(RABBIT_INSTRUMENTATION_NAME)
                    || metric
                        .getInstrumentationScopeInfo()
                        .getName()
                        .equals(SPRING_INSTRUMENTATION_NAME))
        .extracting(MetricData::getName)
        .doesNotContain(
            "messaging.publish.duration",
            "messaging.receive.duration",
            "messaging.receive.messages");
  }

  private SpringRabbitMetricsAssertions() {}
}
