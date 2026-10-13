/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.stats.Avg;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OpenTelemetryMetricsReporterTest {

  private final Meter meter = mock(Meter.class, RETURNS_DEEP_STUBS);
  private final OpenTelemetryMetricsReporter reporter = new OpenTelemetryMetricsReporter();

  @BeforeEach
  void configure() {
    OpenTelemetry openTelemetry = mock(OpenTelemetry.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.meterBuilder("test").build()).thenReturn(meter);
    Map<String, Object> config = new HashMap<>();
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(openTelemetry));
    config.put(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME, "test");
    reporter.configure(config);
  }

  @Test
  void closePreservesRegistrationOrderAndRemovesEachSuccessfulClose() {
    ObservableDoubleGauge first = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge second = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge third = mock(ObservableDoubleGauge.class);
    registerMetric("first", emptyMap(), first);
    registerMetric("second", emptyMap(), second);
    registerMetric("third", emptyMap(), third);
    doAnswer(
            invocation -> {
              assertThat(reporter.getRegisteredObservables())
                  .extracting(RegisteredObservable::getObservable)
                  .containsExactly(second, third);
              return null;
            })
        .when(second)
        .close();

    reporter.close();

    InOrder order = inOrder(first, second, third);
    order.verify(first).close();
    order.verify(second).close();
    order.verify(third).close();
    assertThat(reporter.getRegisteredObservables()).isEmpty();

    reporter.close();

    verify(first).close();
    verify(second).close();
    verify(third).close();
  }

  @Test
  void failedCloseRetainsFailedAndUnattemptedRegistrationsForRetry() {
    ObservableDoubleGauge first = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge second = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge third = mock(ObservableDoubleGauge.class);
    registerMetric("first", emptyMap(), first);
    registerMetric("second", emptyMap(), second);
    registerMetric("third", emptyMap(), third);
    IllegalStateException failure = new IllegalStateException("close failed");
    doThrow(failure).doNothing().when(second).close();

    assertThatThrownBy(reporter::close)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Error occurred closing instrument")
        .hasCause(failure);
    verify(first).close();
    verify(second).close();
    verify(third, never()).close();
    assertThat(reporter.getRegisteredObservables())
        .extracting(RegisteredObservable::getObservable)
        .containsExactly(second, third);

    reporter.close();

    verify(first).close();
    verify(second, times(2)).close();
    verify(third).close();
    assertThat(reporter.getRegisteredObservables()).isEmpty();
  }

  @Test
  void replacementMovesRegistrationToEndAndRemovalClosesOnlyMatchingMetric() {
    ObservableDoubleGauge first = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge second = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge replacement = mock(ObservableDoubleGauge.class);
    KafkaMetric firstMetric = registerMetric("first", emptyMap(), first);
    registerMetric("second", emptyMap(), second);

    registerMetric("first", emptyMap(), replacement);

    verify(first).close();
    assertThat(reporter.getRegisteredObservables())
        .extracting(RegisteredObservable::getObservable)
        .containsExactly(second, replacement);

    reporter.metricRemoval(firstMetric);

    verify(replacement).close();
    assertThat(reporter.getRegisteredObservables())
        .extracting(RegisteredObservable::getObservable)
        .containsExactly(second);

    reporter.close();

    InOrder order = inOrder(first, replacement, second);
    order.verify(first).close();
    order.verify(replacement).close();
    order.verify(second).close();
    assertThat(reporter.getRegisteredObservables()).isEmpty();
  }

  @Test
  void higherDimensionReplacementClosesRegistrationsInOrder() {
    ObservableDoubleGauge first = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge second = mock(ObservableDoubleGauge.class);
    ObservableDoubleGauge replacement = mock(ObservableDoubleGauge.class);
    registerMetric("records", singletonMap("topic", "test-topic"), first);
    registerMetric("records", singletonMap("client-id", "test-client"), second);
    Map<String, String> tags = new HashMap<>();
    tags.put("topic", "test-topic");
    tags.put("client-id", "test-client");

    registerMetric("records", tags, replacement);

    InOrder order = inOrder(first, second, replacement);
    order.verify(first).close();
    order.verify(second).close();
    assertThat(reporter.getRegisteredObservables())
        .extracting(RegisteredObservable::getObservable)
        .containsExactly(replacement);

    reporter.close();

    order.verify(replacement).close();
    assertThat(reporter.getRegisteredObservables()).isEmpty();
  }

  private KafkaMetric registerMetric(
      String name, Map<String, String> tags, ObservableDoubleGauge observable) {
    when(meter
            .gaugeBuilder("kafka.producer." + name)
            .setDescription("description")
            .buildWithCallback(any()))
        .thenReturn(observable);
    KafkaMetric metric = mock(KafkaMetric.class);
    when(metric.metricName())
        .thenReturn(new MetricName(name, "producer-metrics", "description", tags));
    when(metric.measurable()).thenReturn(new Avg());
    reporter.metricChange(metric);
    return metric;
  }
}
