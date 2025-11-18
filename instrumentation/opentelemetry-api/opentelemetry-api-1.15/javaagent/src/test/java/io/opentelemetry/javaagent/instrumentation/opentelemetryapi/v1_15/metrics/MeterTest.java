/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
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
  void batchLongCounter() throws InterruptedException {
    ObservableLongMeasurement observableMeasurement =
        meter.counterBuilder("test").setDescription("d").setUnit("u").buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(11, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void batchDoubleCounter() throws InterruptedException {
    ObservableDoubleMeasurement observableMeasurement =
        meter.counterBuilder("test").ofDoubles().setDescription("d").setUnit("u").buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(12.1, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void batchLongUpDownCounter() throws InterruptedException {
    ObservableLongMeasurement observableMeasurement =
        meter.upDownCounterBuilder("test").setDescription("d").setUnit("u").buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(11, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void batchDoubleUpDownCounter() throws InterruptedException {
    ObservableDoubleMeasurement observableMeasurement =
        meter
            .upDownCounterBuilder("test")
            .ofDoubles()
            .setDescription("d")
            .setUnit("u")
            .buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(12.1, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void batchLongGauge() throws InterruptedException {
    ObservableLongMeasurement observableMeasurement =
        meter.gaugeBuilder("test").ofLongs().setDescription("d").setUnit("u").buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(123, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void batchDoubleGauge() throws InterruptedException {
    ObservableDoubleMeasurement observableMeasurement =
        meter.gaugeBuilder("test").setDescription("d").setUnit("u").buildObserver();

    BatchCallback callback =
        meter.batchCallback(
            () -> {
              observableMeasurement.record(1.23, Attributes.of(AttributeKey.stringKey("q"), "r"));
            },
            observableMeasurement);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
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
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));

    callback.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }
}
