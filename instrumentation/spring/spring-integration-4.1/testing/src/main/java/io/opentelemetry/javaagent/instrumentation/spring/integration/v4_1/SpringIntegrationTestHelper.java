/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION;
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

  @SuppressWarnings("deprecation") // using deprecated semconv
  static AttributeAssertion[] messagingAttributes(
      String operationName, String destinationName, AttributeAssertion... additionalAssertions) {
    // the old semantic conventions used "publish" where the stable ones use "send"
    String oldOperation = operationName.equals("send") ? "publish" : operationName;
    AttributeAssertion[] standard =
        new AttributeAssertion[] {
          equalTo(MESSAGING_SYSTEM, emitStableMessagingSemconv() ? "spring_integration" : null),
          equalTo(
              MESSAGING_DESTINATION_NAME, emitStableMessagingSemconv() ? destinationName : null),
          equalTo(MESSAGING_OPERATION, emitOldMessagingSemconv() ? oldOperation : null),
          equalTo(MESSAGING_OPERATION_NAME, emitStableMessagingSemconv() ? operationName : null),
          equalTo(MESSAGING_OPERATION_TYPE, emitStableMessagingSemconv() ? operationName : null)
        };
    AttributeAssertion[] result =
        new AttributeAssertion[standard.length + additionalAssertions.length];
    System.arraycopy(standard, 0, result, 0, standard.length);
    System.arraycopy(additionalAssertions, 0, result, standard.length, additionalAssertions.length);
    return result;
  }

  static void assertProcessMetrics(
      InstrumentationExtension testing, String destinationName, boolean failed) {
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
                    assertThat(metric)
                        .hasUnit("s")
                        .hasDescription("Duration of processing operation.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME, destinationName),
                                                errorType)))));
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
                                sum.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "process"),
                                                equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME, destinationName),
                                                errorType)))));
  }

  static void assertSendMetrics(InstrumentationExtension testing, String destinationName) {
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
                                            .hasCount(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME, destinationName),
                                                equalTo(MESSAGING_OPERATION_TYPE, "send"))))));
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
                                                equalTo(MESSAGING_OPERATION_NAME, "send"),
                                                equalTo(MESSAGING_SYSTEM, "spring_integration"),
                                                equalTo(
                                                    MESSAGING_DESTINATION_NAME,
                                                    destinationName))))));
  }

  static void assertNoMetrics(InstrumentationExtension testing) {
    assertThat(testing.metrics())
        .noneMatch(
            metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME));
  }

  private SpringIntegrationTestHelper() {}
}
