/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_OPERATION_TYPE;
import static io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes.MESSAGING_SYSTEM;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("deprecation") // using deprecated semconv
abstract class AbstractNatsTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.nats-2.17";
  private static final double[] DURATION_BUCKETS = {
    0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0
  };

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private GenericContainer<?> natsContainer;
  Connection connection;

  protected abstract InstrumentationExtension testing();

  void assertProducerMetrics(String operationName, String destination, String errorType) {

    testing()
        .waitAndAssertMetrics(
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
                                        .hasSumGreaterThan(0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, "nats"),
                                            equalTo(ERROR_TYPE, errorType),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination),
                                            equalTo(MESSAGING_OPERATION_TYPE, "send"))
                                        .hasBucketBoundaries(DURATION_BUCKETS))
                            .accept(assertThat(metric))));
    testing()
        .waitAndAssertMetrics(
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
                                            equalTo(MESSAGING_OPERATION_NAME, operationName),
                                            equalTo(MESSAGING_SYSTEM, "nats"),
                                            equalTo(ERROR_TYPE, errorType),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination)))
                            .accept(assertThat(metric))));
  }

  void assertProcessMetrics(String destination, String errorType) {

    testing()
        .waitAndAssertMetrics(
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
                                        .hasSumGreaterThan(0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                                            equalTo(MESSAGING_SYSTEM, "nats"),
                                            equalTo(ERROR_TYPE, errorType),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination))
                                        .hasBucketBoundaries(DURATION_BUCKETS))
                            .accept(assertThat(metric))));
    testing()
        .waitAndAssertMetrics(
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
                                        .hasValue(1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(MESSAGING_OPERATION_NAME, "process"),
                                            equalTo(MESSAGING_SYSTEM, "nats"),
                                            equalTo(ERROR_TYPE, errorType),
                                            equalTo(MESSAGING_DESTINATION_NAME, destination)))
                            .accept(assertThat(metric))));
  }

  @BeforeAll
  void beforeAll() throws IOException, InterruptedException {
    DockerImageName natsImage = DockerImageName.parse("nats:2.11.2-alpine3.21");

    natsContainer = new GenericContainer<>(natsImage).withExposedPorts(4222);
    cleanup.deferAfterAll(natsContainer);
    natsContainer.start();

    String host = natsContainer.getHost();
    int port = natsContainer.getMappedPort(4222);
    connection = Nats.connect("nats://" + host + ":" + port);
    cleanup.deferAfterAll(() -> connection.drain(Duration.ZERO));
  }
}
