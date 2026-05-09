/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import java.lang.reflect.Method;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.RegisterExtension;

class MeterTest {

  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  private String instrumentationName;
  private Meter meter;

  @BeforeEach
  void setupMeter(TestInfo test) {
    instrumentationName = "test-" + test.getDisplayName();
    meter =
        testing
            .getOpenTelemetry()
            .getMeterProvider()
            .meterBuilder(instrumentationName)
            .setInstrumentationVersion("1.2.3")
            .setSchemaUrl("http://schema.org")
            .build();
  }

  @Test
  void longCounter() {
    LongCounter instrument = meter.counterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5, Attributes.of(stringKey("q"), "r"));
    instrument.add(6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(11)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));
  }

  @Test
  void observableLongCounter() {
    ObservableLongCounter observableCounter =
        meter
            .counterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(11, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasLongSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(11)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));

    close(observableCounter);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void doubleCounter() {
    DoubleCounter instrument =
        meter.counterBuilder("test").ofDoubles().setDescription("d").setUnit("u").build();

    instrument.add(5.5, Attributes.of(stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(12.1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));
  }

  @Test
  void observableDoubleCounter() {
    ObservableDoubleCounter observableCounter =
        meter
            .counterBuilder("test")
            .ofDoubles()
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(12.1, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.isMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(12.1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));

    close(observableCounter);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void longUpDownCounter() {
    LongUpDownCounter instrument =
        meter.upDownCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5, Attributes.of(stringKey("q"), "r"));
    instrument.add(6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(11)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));
  }

  @Test
  void observableLongUpDownCounter() {
    ObservableLongUpDownCounter observableCounter =
        meter
            .upDownCounterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(11, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasLongSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(11)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));

    close(observableCounter);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void doubleUpDownCounter() {
    DoubleUpDownCounter instrument =
        meter.upDownCounterBuilder("test").ofDoubles().setDescription("d").setUnit("u").build();

    instrument.add(5.5, Attributes.of(stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(12.1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));
  }

  @Test
  void observableDoubleUpDownCounter() {
    ObservableDoubleUpDownCounter observableCounter =
        meter
            .upDownCounterBuilder("test")
            .ofDoubles()
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(12.1, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.isNotMonotonic()
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(12.1)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("q"), "r")))));

    close(observableCounter);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void longHistogram() {
    LongHistogram instrument =
        meter.histogramBuilder("test").ofLongs().setDescription("d").setUnit("u").build();

    instrument.record(5, Attributes.of(stringKey("q"), "r"));
    instrument.record(6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasSum(11.0)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void doubleHistogram() {
    DoubleHistogram instrument =
        meter.histogramBuilder("test").setDescription("d").setUnit("u").build();

    instrument.record(5.5, Attributes.of(stringKey("q"), "r"));
    instrument.record(6.6, Attributes.of(stringKey("q"), "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasSum(12.1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void longGauge() {
    ObservableLongGauge observableGauge =
        meter
            .gaugeBuilder("test")
            .ofLongs()
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(123, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasLongGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(123)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));

    close(observableGauge);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void doubleGauge() {
    ObservableDoubleGauge observableGauge =
        meter
            .gaugeBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .buildWithCallback(result -> result.record(1.23, Attributes.of(stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        metric ->
            metric
                .hasName("test")
                .hasDescription("d")
                .hasUnit("u")
                .hasInstrumentationScope(
                    InstrumentationScopeInfo.builder(instrumentationName)
                        .setVersion("1.2.3")
                        .setSchemaUrl("http://schema.org")
                        .build())
                .hasDoubleGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(1.23)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));

    close(observableGauge);

    testing.clearData();

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  private static void close(Object observableInstrument) {
    // our bridge includes close method, although it was added in 1.12
    try {
      Method close = observableInstrument.getClass().getDeclaredMethod("close");
      close.invoke(observableInstrument);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Failed to call close", e);
    }
  }
}
