/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;
import static io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BoundDoubleCounter;
import io.opentelemetry.api.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.metrics.BoundDoubleValueRecorder;
import io.opentelemetry.api.metrics.BoundLongCounter;
import io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.metrics.BoundLongValueRecorder;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleValueRecorder;
import io.opentelemetry.api.metrics.GlobalMeterProvider;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongValueRecorder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.common.Labels;
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
    meter = GlobalMeterProvider.getMeter(instrumentationName, "1.2.3");
  }

  @Test
  void longCounter() {
    LongCounter instrument =
        meter.longCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5, Labels.of("q", "r"));
    instrument.add(6, Labels.of("q", "r"));

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
  void longCounter_bound() {
    BoundLongCounter instrument =
        meter
            .longCounterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.add(5);
    instrument.add(6);

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
                                    .containsOnly(attributeEntry("w", "x")))));
  }

  @Test
  void longUpDownCounter() {
    LongUpDownCounter instrument =
        meter.longUpDownCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5, Labels.of("q", "r"));
    instrument.add(6, Labels.of("q", "r"));

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
  void longUpDownCounter_bound() {
    BoundLongUpDownCounter instrument =
        meter
            .longUpDownCounterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.add(5);
    instrument.add(6);

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
                                    .containsOnly(attributeEntry("w", "x")))));
  }

  @Test
  void doubleCounter() {
    DoubleCounter instrument =
        meter.doubleCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5.5, Labels.of("q", "r"));
    instrument.add(6.6, Labels.of("q", "r"));

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
  void doubleCounter_bound() {
    BoundDoubleCounter instrument =
        meter
            .doubleCounterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.add(5.5);
    instrument.add(6.6);

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
                                    .containsOnly(attributeEntry("w", "x")))));
  }

  @Test
  void doubleUpDownCounter() {
    DoubleUpDownCounter instrument =
        meter.doubleUpDownCounterBuilder("test").setDescription("d").setUnit("u").build();

    instrument.add(5.5, Labels.of("q", "r"));
    instrument.add(6.6, Labels.of("q", "r"));

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
  void doubleUpDownCounter_bound() {
    BoundDoubleUpDownCounter instrument =
        meter
            .doubleUpDownCounterBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.add(5.5);
    instrument.add(6.6);

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
                                    .containsOnly(attributeEntry("w", "x")))));
  }

  @Test
  void longValueRecorder() {
    LongValueRecorder instrument =
        meter.longValueRecorderBuilder("test").setDescription("d").setUnit("u").build();

    instrument.record(5, Labels.of("q", "r"));
    instrument.record(6, Labels.of("q", "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  // No helper assertion for summary yet it seems.
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"));
                  assertThat(metric.getDoubleSummaryData().getPoints())
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(11.0);
                            assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("q"), "r"));
                          });
                }));
  }

  @Test
  void longValueRecorder_bound() {
    BoundLongValueRecorder instrument =
        meter
            .longValueRecorderBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.record(5);
    instrument.record(6);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  // No helper assertion for summary yet it seems.
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"));
                  assertThat(metric.getDoubleSummaryData().getPoints())
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(11.0);
                            assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("w"), "x"));
                          });
                }));
  }

  @Test
  void doubleValueRecorder() {
    DoubleValueRecorder instrument =
        meter.doubleValueRecorderBuilder("test").setDescription("d").setUnit("u").build();

    instrument.record(5.5, Labels.of("q", "r"));
    instrument.record(6.6, Labels.of("q", "r"));

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  // No helper assertion for summary yet it seems.
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"));
                  assertThat(metric.getDoubleSummaryData().getPoints())
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(12.1);
                            assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("q"), "r"));
                          });
                }));
  }

  @Test
  void doubleValueRecorder_bound() {
    BoundDoubleValueRecorder instrument =
        meter
            .doubleValueRecorderBuilder("test")
            .setDescription("d")
            .setUnit("u")
            .build()
            .bind(Labels.of("w", "x"));

    instrument.record(5.5);
    instrument.record(6.6);

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  // No helper assertion for summary yet it seems.
                  assertThat(metric)
                      .hasDescription("d")
                      .hasUnit("u")
                      .hasInstrumentationLibrary(
                          InstrumentationLibraryInfo.create(instrumentationName, "1.2.3"));
                  assertThat(metric.getDoubleSummaryData().getPoints())
                      .allSatisfy(
                          point -> {
                            assertThat(point.getSum()).isEqualTo(12.1);
                            assertThat(point.getAttributes())
                                .isEqualTo(Attributes.of(AttributeKey.stringKey("w"), "x"));
                          });
                }));
  }

  @Test
  void longSumObserver() {
    meter
        .longSumObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(123, Labels.of("q", "r")))
        .build();

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
                                    .hasValue(123)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
  }

  @Test
  void longUpDownSumObserver() {
    meter
        .longUpDownSumObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(123, Labels.of("q", "r")))
        .build();

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
                                    .hasValue(123)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
  }

  @Test
  void longValueObserver() {
    meter
        .longValueObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(123, Labels.of("q", "r")))
        .build();

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
  void doubleSumObserver() {
    meter
        .doubleSumObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(1.23, Labels.of("q", "r")))
        .build();

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
                                    .hasValue(1.23)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
  }

  @Test
  void doubleUpDownSumObserver() {
    meter
        .doubleUpDownSumObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(1.23, Labels.of("q", "r")))
        .build();

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
                                    .hasValue(1.23)
                                    .attributes()
                                    .containsOnly(attributeEntry("q", "r")))));
  }

  @Test
  void doubleValueObserver() {
    meter
        .doubleValueObserverBuilder("test")
        .setDescription("d")
        .setUnit("u")
        .setUpdater(result -> result.observe(1.23, Labels.of("q", "r")))
        .build();

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

  @Test
  void testBatchRecorder() {
    LongCounter longCounter =
        meter.longCounterBuilder("test").setDescription("d").setUnit("u").build();
    DoubleCounter doubleCounter =
        meter.doubleCounterBuilder("test2").setDescription("d").setUnit("u").build();

    meter
        .newBatchRecorder("q", "r")
        .put(longCounter, 5)
        .put(longCounter, 6)
        .put(doubleCounter, 5.5)
        .put(doubleCounter, 6.6)
        .record();

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

    testing.waitAndAssertMetrics(
        instrumentationName,
        "test2",
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
}
