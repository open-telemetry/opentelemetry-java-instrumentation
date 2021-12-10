/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

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
import io.opentelemetry.sdk.common.InstrumentationLibraryInfo;
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
    meter = testing.getOpenTelemetry().getMeterProvider().get(instrumentationName);
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
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                        .hasLongSum()
                        .isMonotonic()
                        .isCumulative()
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
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                        .hasLongSum()
                        .isNotMonotonic()
                        .isCumulative()
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
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                        .hasDoubleSum()
                        .isMonotonic()
                        .isCumulative()
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
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                        .hasDoubleSum()
                        .isNotMonotonic()
                        .isCumulative()
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
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                      .hasDoubleHistogram()
                      .points()
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(11.0);
                            assertThat(point.getAttributes())
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
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
                      .hasDoubleHistogram()
                      .points()
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(12.1);
                            assertThat(point.getAttributes())
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
            result -> result.observe(123, Attributes.of(AttributeKey.stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("d")
                        .hasUnit("u")
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
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
            result -> result.observe(1.23, Attributes.of(AttributeKey.stringKey("q"), "r")));

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDescription("d")
                        .hasUnit("u")
                        .hasInstrumentationLibrary(
                            InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"))
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
