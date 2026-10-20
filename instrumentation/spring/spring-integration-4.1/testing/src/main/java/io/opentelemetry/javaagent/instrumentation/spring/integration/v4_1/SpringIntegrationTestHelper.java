/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import org.assertj.core.api.AbstractStringAssert;

final class SpringIntegrationTestHelper {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-integration-4.1";

  static AttributeAssertion[] messagingAttributes(String operationName, String destinationName) {
    return messagingAttributes(operationName, destinationName, new AttributeAssertion[0]);
  }

  static AttributeAssertion[] messagingAttributes(
      String operationName, String destinationName, AttributeAssertion... additionalAssertions) {
    AttributeAssertion[] standard =
        new AttributeAssertion[] {
          equalTo(MESSAGING_SYSTEM, "spring_integration"),
          equalTo(MESSAGING_DESTINATION_NAME, destinationName),
          equalTo(MESSAGING_OPERATION_NAME, operationName),
          equalTo(MESSAGING_OPERATION_TYPE, operationName)
        };
    AttributeAssertion[] result =
        new AttributeAssertion[standard.length + additionalAssertions.length];
    System.arraycopy(standard, 0, result, 0, standard.length);
    System.arraycopy(additionalAssertions, 0, result, standard.length, additionalAssertions.length);
    return result;
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing, String destinationName, boolean failed) {
    assertProcessMetrics(testing, destinationName, failed, 1);
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing,
      String destinationName,
      boolean failed,
      long expectedCount) {
    AttributeAssertion errorType =
        failed
            ? satisfies(ERROR_TYPE, AbstractStringAssert::isNotEmpty)
            : equalTo(ERROR_TYPE, null);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.process.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    histogram(
                            metricAssert ->
                                metricAssert
                                    .hasUnit("s")
                                    .hasDescription("Duration of processing operation."),
                            point ->
                                point
                                    .hasCount(expectedCount)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "process"),
                                        equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                        equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                                        errorType))
                        .accept(assertThat(metric))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.consumed.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    longSum(
                            metricAssert ->
                                metricAssert
                                    .hasUnit("{message}")
                                    .hasDescription(
                                        "Number of messages that were delivered to the application."),
                            point ->
                                point
                                    .hasValue(expectedCount)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "process"),
                                        equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                        equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                                        errorType))
                        .accept(assertThat(metric))));
  }

  static void assertSendMetrics(InstrumentationExtension testing, String destinationName) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.operation.duration",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    histogram(
                            metricAssert ->
                                metricAssert
                                    .hasUnit("s")
                                    .hasDescription(
                                        "Duration of messaging operation initiated by a producer or consumer client."),
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "send"),
                                        equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                        equalTo(MESSAGING_DESTINATION_NAME, destinationName),
                                        equalTo(MESSAGING_OPERATION_TYPE, "send")))
                        .accept(assertThat(metric))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "messaging.client.sent.messages",
        metrics ->
            metrics.satisfiesExactly(
                metric ->
                    longSum(
                            metricAssert ->
                                metricAssert
                                    .hasUnit("{message}")
                                    .hasDescription(
                                        "Number of messages producer attempted to send to the broker."),
                            point ->
                                point
                                    .hasValue(1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(MESSAGING_OPERATION_NAME, "send"),
                                        equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                        equalTo(MESSAGING_DESTINATION_NAME, destinationName)))
                        .accept(assertThat(metric))));
  }

  static void assertNoMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .noneMatch(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME));
  }

  private SpringIntegrationTestHelper() {}
}
