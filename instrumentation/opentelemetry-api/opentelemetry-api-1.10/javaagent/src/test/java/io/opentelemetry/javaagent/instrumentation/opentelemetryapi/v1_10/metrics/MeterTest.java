/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
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

    instrument.add(5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.add(6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(11)
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void longUpDownCounter() {
    LongUpDownCounter instrument =
        meter.upDownCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.add(6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(11)
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void doubleCounter() {
    DoubleCounter instrument =
        meter.counterBuilder("test").ofDoubles().setDescription("d").setUnit("u").build();

    instrument.add(5.5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12.1)
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void doubleUpDownCounter() {
    DoubleUpDownCounter instrument =
        meter.upDownCounterBuilder("test").ofDoubles().setDescription("d").setUnit("u").build();

    instrument.add(5.5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12.1)
                                                .hasAttributesSatisfying(
                                                    equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void longHistogram() {
    LongHistogram instrument =
        meter.histogramBuilder("test").ofLongs().setDescription("d").setUnit("u").build();

    instrument.record(5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.record(6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(11.0)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void doubleHistogram() {
    DoubleHistogram instrument =
        meter.histogramBuilder("test").setDescription("d").setUnit("u").build();

    instrument.record(5.5, Attributes.of(AttributeKey.stringKey("q"), "r"));
    instrument.record(6.6, Attributes.of(AttributeKey.stringKey("q"), "r"));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(12.1)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void longGauge() {
    meter
        .gaugeBuilder("test")
        .ofLongs()
        .setDescription("d")
        .setUnit("u")
        .buildWithCallback(
            result -> result.record(123, Attributes.of(AttributeKey.stringKey("q"), "r")));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasLongGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(123)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));
  }

  @Test
  void doubleGauge() {
    meter
        .gaugeBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .buildWithCallback(
            result -> result.record(1.23, Attributes.of(AttributeKey.stringKey("q"), "r")));

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
                            InstrumentationScopeInfo.create(
                                instrumentationName, "1.2.3", /* schemaUrl= */ null))
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1.23)
                                            .hasAttributesSatisfying(
                                                equalTo(AttributeKey.stringKey("q"), "r"))))));
  }
}
