/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_PARTITION_ID;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.HashSet;
import java.util.Set;

public class CamelMessagingMetricsAssertions {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.camel-2.20";
  private static final Set<String> DEPRECATED_METRICS =
      new HashSet<>(
          asList(
              "messaging.publish.duration",
              "messaging.receive.duration",
              "messaging.receive.messages"));

  public static void assertSendAndProcessMetrics(
      InstrumentationExtension testing, String system, String destination) {
    assertSendAndProcessMetrics(testing, system, destination, null, null);
  }

  public static void assertSendAndProcessMetrics(
      InstrumentationExtension testing,
      String system,
      String destination,
      String processErrorType) {
    assertSendAndProcessMetrics(testing, system, destination, processErrorType, null);
  }

  public static void assertSendAndProcessMetrics(
      InstrumentationExtension testing,
      String system,
      String destination,
      String processErrorType,
      String processDestinationPartitionId) {
    if (!emitStableMessagingSemconv()) {
      assertNoCamelMessagingMetrics(testing);
      return;
    }

    assertDuration(
        testing,
        "messaging.client.operation.duration",
        "Duration of messaging operation initiated by a producer or consumer client.",
        "send",
        "send",
        system,
        destination,
        null,
        null);
    assertCounter(
        testing,
        "messaging.client.sent.messages",
        "Number of messages producer attempted to send to the broker.",
        "send",
        system,
        destination,
        null,
        null);
    assertDuration(
        testing,
        "messaging.process.duration",
        "Duration of processing operation.",
        "process",
        null,
        system,
        destination,
        processErrorType,
        processDestinationPartitionId);
    assertCounter(
        testing,
        "messaging.client.consumed.messages",
        "Number of messages that were delivered to the application.",
        "process",
        system,
        destination,
        processErrorType,
        processDestinationPartitionId);
    assertNoDeprecatedMetrics(testing);
  }

  public static void assertSendMetrics(
      InstrumentationExtension testing, String system, String destination, String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoCamelMessagingMetrics(testing);
      return;
    }

    assertDuration(
        testing,
        "messaging.client.operation.duration",
        "Duration of messaging operation initiated by a producer or consumer client.",
        "send",
        "send",
        system,
        destination,
        errorType,
        null);
    assertCounter(
        testing,
        "messaging.client.sent.messages",
        "Number of messages producer attempted to send to the broker.",
        "send",
        system,
        destination,
        errorType,
        null);
    assertNoDeprecatedMetrics(testing);
  }

  public static void assertProcessMetrics(
      InstrumentationExtension testing, String system, String destination) {
    assertProcessMetrics(testing, system, destination, null);
  }

  public static void assertProcessMetrics(
      InstrumentationExtension testing, String system, String destination, String errorType) {
    if (!emitStableMessagingSemconv()) {
      assertNoCamelMessagingMetrics(testing);
      return;
    }

    assertDuration(
        testing,
        "messaging.process.duration",
        "Duration of processing operation.",
        "process",
        null,
        system,
        destination,
        errorType,
        null);
    assertCounter(
        testing,
        "messaging.client.consumed.messages",
        "Number of messages that were delivered to the application.",
        "process",
        system,
        destination,
        errorType,
        null);
    assertThat(camelMetrics(testing))
        .noneMatch(
            metric ->
                metric.getName().equals("messaging.client.operation.duration")
                    || metric.getName().equals("messaging.client.sent.messages"));
    assertNoDeprecatedMetrics(testing);
  }

  public static void assertNoCamelMessagingMetrics(InstrumentationExtension testing) {
    assertThat(camelMetrics(testing))
        .noneMatch(metric -> metric.getName().startsWith("messaging."));
  }

  private static void assertDuration(
      InstrumentationExtension testing,
      String metricName,
      String description,
      String operationName,
      String operationType,
      String system,
      String destination,
      String errorType,
      String destinationPartitionId) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.satisfiesExactly(
                metric -> {
                  assertThat(metric)
                      .hasUnit("s")
                      .hasDescription(description)
                      .hasHistogramSatisfying(
                          histogram ->
                              histogram.hasPointsSatisfying(
                                  point -> {
                                    point.hasCount(1);
                                    if (operationType != null) {
                                      if (destinationPartitionId != null) {
                                        point.hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, system),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination),
                                            equalTo(
                                                MESSAGING_DESTINATION_PARTITION_ID,
                                                destinationPartitionId),
                                            equalTo(MESSAGING_OPERATION_TYPE, operationType),
                                            equalTo(ERROR_TYPE, errorType));
                                      } else {
                                        point.hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, system),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination),
                                            equalTo(MESSAGING_OPERATION_TYPE, operationType),
                                            equalTo(ERROR_TYPE, errorType));
                                      }
                                    } else {
                                      if (destinationPartitionId != null) {
                                        point.hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, system),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination),
                                            equalTo(
                                                MESSAGING_DESTINATION_PARTITION_ID,
                                                destinationPartitionId),
                                            equalTo(ERROR_TYPE, errorType));
                                      } else {
                                        point.hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, system),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination),
                                            equalTo(ERROR_TYPE, errorType));
                                      }
                                    }
                                  }));
                }));
  }

  private static void assertCounter(
      InstrumentationExtension testing,
      String metricName,
      String description,
      String operationName,
      String system,
      String destination,
      String errorType,
      String destinationPartitionId) {
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metricName,
        metrics ->
            metrics.satisfiesExactly(
                metric -> {
                  assertThat(metric)
                      .hasUnit("{message}")
                      .hasDescription(description)
                      .hasLongSumSatisfying(
                          sum ->
                              sum.hasPointsSatisfying(
                                  point -> {
                                    point.hasValue(1);
                                    if (destinationPartitionId != null) {
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(MESSAGING_OPERATION_NAME, operationName),
                                          equalTo(MESSAGING_SYSTEM, system),
                                          equalTo(MESSAGING_DESTINATION_NAME, destination),
                                          equalTo(
                                              MESSAGING_DESTINATION_PARTITION_ID,
                                              destinationPartitionId),
                                          equalTo(ERROR_TYPE, errorType));
                                    } else {
                                      point.hasAttributesSatisfyingExactly(
                                          equalTo(MESSAGING_OPERATION_NAME, operationName),
                                          equalTo(MESSAGING_SYSTEM, system),
                                          equalTo(MESSAGING_DESTINATION_NAME, destination),
                                          equalTo(ERROR_TYPE, errorType));
                                    }
                                  }));
                }));
  }

  private static void assertNoDeprecatedMetrics(InstrumentationExtension testing) {
    assertThat(camelMetrics(testing))
        .noneMatch(metric -> DEPRECATED_METRICS.contains(metric.getName()));
  }

  private static Set<MetricData> camelMetrics(InstrumentationExtension testing) {
    return testing.metrics().stream()
        .filter(
            metric -> INSTRUMENTATION_NAME.equals(metric.getInstrumentationScopeInfo().getName()))
        .collect(toSet());
  }

  private CamelMessagingMetricsAssertions() {}
}
