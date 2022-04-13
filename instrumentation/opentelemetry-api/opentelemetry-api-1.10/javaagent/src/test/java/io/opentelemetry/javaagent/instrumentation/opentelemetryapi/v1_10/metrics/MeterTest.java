/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import static io.opentelemetry.sdk.testing.assertj.MetricAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

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
import org.assertj.core.api.Assertions;
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
                        .hasLongSum()
                        .isMonotonic()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(11)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
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
                        .hasLongSum()
                        .isNotMonotonic()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(11)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
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
                        .hasDoubleSum()
                        .isMonotonic()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(12.1)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
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
                        .hasDoubleSum()
                        .isNotMonotonic()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(12.1)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
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
                metric -> {
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationScope(
                          InstrumentationScopeInfo.create(
                              instrumentationName, "1.2.3", /* schemaUrl= */ null))
                      .hasDoubleHistogram()
                      .points()
                      .allSatisfy(
                          point -> {
                            Assertions.assertThat(point.getSum()).isEqualTo(11.0);
                            Assertions.assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("q"), "r"));
                          });
                }));
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
                metric -> {
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationScope(
                          InstrumentationScopeInfo.create(
                              instrumentationName, "1.2.3", /* schemaUrl= */ null))
                      .hasDoubleHistogram()
                      .points()
                      .allSatisfy(
                          point -> {
                            Assertions.assertThat(point.getSum()).isEqualTo(12.1);
                            Assertions.assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("q"), "r"));
                          });
                }));
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
                        .hasLongGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(123)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
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
                        .hasDoubleGauge()
                        .points()
                        .satisfiesExactly(
                            point ->
                                assertThat(point)
                                    .hasValue(1.23)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
  }
}
