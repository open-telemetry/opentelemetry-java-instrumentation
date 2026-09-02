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
    if (system.equals("jms") || system.equals("kafka")) {
      assertConsumedMessageCount(testing, system, destination, 1);
    } else {
      assertCounter(
          testing,
          "messaging.client.consumed.messages",
          "Number of messages that were delivered to the application.",
          "process",
          system,
          destination,
          processErrorType,
          processDestinationPartitionId);
    }
    assertNoDeprecatedMetrics(testing);
    if (system.equals("jms") || system.equals("kafka")) {
      assertNoDuplicateMessagingMetrics(testing, system, destination);
    }
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
    assertNoDuplicateMessagingMetric(
        testing, "messaging.client.sent.messages", "send", system, destination);
    assertNoDuplicateMessagingMetric(
        testing, "messaging.client.operation.duration", "send", system, destination);
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

  private static void assertNoCamelMessagingMetrics(InstrumentationExtension testing) {
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

  private static void assertNoDuplicateMessagingMetrics(
      InstrumentationExtension testing, String system, String destination) {
    assertNoDuplicateMessagingMetric(
        testing, "messaging.client.sent.messages", "send", system, destination);
    assertNoDuplicateMessagingMetric(
        testing, "messaging.client.operation.duration", "send", system, destination);
    assertNoDuplicateMessagingMetric(
        testing, "messaging.client.consumed.messages", "process", system, destination);
    assertNoDuplicateMessagingMetric(
        testing, "messaging.process.duration", "process", system, destination);
  }

  private static void assertConsumedMessageCount(
      InstrumentationExtension testing, String system, String destination, long expectedCount) {
    long count =
        testing.metrics().stream()
            .filter(metric -> metric.getName().equals("messaging.client.consumed.messages"))
            .flatMap(metric -> metric.getLongSumData().getPoints().stream())
            .filter(
                point ->
                    system.equals(point.getAttributes().get(MESSAGING_SYSTEM))
                        && destination.equals(
                            point.getAttributes().get(MESSAGING_DESTINATION_NAME)))
            .mapToLong(point -> point.getValue())
            .sum();
    assertThat(count).isEqualTo(expectedCount);
  }

  private static void assertNoDuplicateMessagingMetric(
      InstrumentationExtension testing,
      String metricName,
      String operationName,
      String system,
      String destination) {
    Set<MetricData> matchingMetrics =
        testing.metrics().stream()
            .filter(metric -> metricName.equals(metric.getName()))
            .filter(
                metric ->
                    metric.getData().getPoints().stream()
                        .anyMatch(
                            point ->
                                operationName.equals(
                                        point.getAttributes().get(MESSAGING_OPERATION_NAME))
                                    && system.equals(point.getAttributes().get(MESSAGING_SYSTEM))
                                    && destination.equals(
                                        point.getAttributes().get(MESSAGING_DESTINATION_NAME))))
            .collect(toSet());
    assertThat(matchingMetrics)
        .noneMatch(
            metric -> !INSTRUMENTATION_NAME.equals(metric.getInstrumentationScopeInfo().getName()));
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
