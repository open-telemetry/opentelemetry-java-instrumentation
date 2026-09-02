/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleCounter;
import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.BoundLongCounter;
import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MeterTest {

  @RegisterExtension
  private static final AgentInstrumentationExtension testing =
      AgentInstrumentationExtension.create();

  @Test
  void boundLongCounter() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-long-counter");
    LongCounterBuilder counterBuilder = meter.counterBuilder("test");
    LongCounter longCounter = counterBuilder.build();
    assertThat(longCounter).isInstanceOf(ExtendedLongCounter.class);

    BoundLongCounter bound =
        ((ExtendedLongCounter) longCounter).bind(Attributes.of(stringKey("q"), "r"));
    assertThat(bound).isInstanceOf(BoundLongCounter.class);
    bound.add(5);
    bound.add(6);

    testing.waitAndAssertMetrics(
        "test-bound-long-counter",
        metric ->
            metric
                .hasName("test")
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(11)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void boundDoubleCounter() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-double-counter");
    DoubleCounter doubleCounter = meter.counterBuilder("test").ofDoubles().build();
    assertThat(doubleCounter).isInstanceOf(ExtendedDoubleCounter.class);

    BoundDoubleCounter bound =
        ((ExtendedDoubleCounter) doubleCounter).bind(Attributes.of(stringKey("q"), "r"));
    bound.add(5.5);
    bound.add(6.6);

    testing.waitAndAssertMetrics(
        "test-bound-double-counter",
        metric ->
            metric
                .hasName("test")
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(12.1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void boundLongUpDownCounter() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-long-up-down-counter");
    LongUpDownCounterBuilder upDownCounterBuilder = meter.upDownCounterBuilder("test");
    LongUpDownCounter longUpDownCounter = upDownCounterBuilder.build();
    assertThat(longUpDownCounter).isInstanceOf(ExtendedLongUpDownCounter.class);

    BoundLongUpDownCounter bound =
        ((ExtendedLongUpDownCounter) longUpDownCounter).bind(Attributes.of(stringKey("q"), "r"));
    bound.add(11);

    testing.waitAndAssertMetrics(
        "test-bound-long-up-down-counter",
        metric ->
            metric
                .hasName("test")
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(11)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void boundDoubleUpDownCounter() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-double-up-down-counter");
    DoubleUpDownCounter doubleUpDownCounter =
        meter.upDownCounterBuilder("test").ofDoubles().build();
    assertThat(doubleUpDownCounter).isInstanceOf(ExtendedDoubleUpDownCounter.class);

    BoundDoubleUpDownCounter bound =
        ((ExtendedDoubleUpDownCounter) doubleUpDownCounter)
            .bind(Attributes.of(stringKey("q"), "r"));
    bound.add(12.1);

    testing.waitAndAssertMetrics(
        "test-bound-double-up-down-counter",
        metric ->
            metric
                .hasName("test")
                .hasDoubleSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(12.1)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void boundLongHistogram() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-long-histogram");
    LongHistogram longHistogram = meter.histogramBuilder("test").ofLongs().build();
    assertThat(longHistogram).isInstanceOf(ExtendedLongHistogram.class);

    BoundLongHistogram bound =
        ((ExtendedLongHistogram) longHistogram).bind(Attributes.of(stringKey("q"), "r"));
    bound.record(11);

    testing.waitAndAssertMetrics(
        "test-bound-long-histogram",
        metric ->
            metric
                .hasName("test")
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
  void boundDoubleHistogram() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-double-histogram");
    DoubleHistogramBuilder histogramBuilder = meter.histogramBuilder("test");
    DoubleHistogram doubleHistogram = histogramBuilder.build();
    assertThat(doubleHistogram).isInstanceOf(ExtendedDoubleHistogram.class);

    BoundDoubleHistogram bound =
        ((ExtendedDoubleHistogram) doubleHistogram).bind(Attributes.of(stringKey("q"), "r"));
    bound.record(12.1);

    testing.waitAndAssertMetrics(
        "test-bound-double-histogram",
        metric ->
            metric
                .hasName("test")
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
  void boundLongGauge() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-long-gauge");
    LongGauge longGauge = meter.gaugeBuilder("test").ofLongs().build();
    assertThat(longGauge).isInstanceOf(ExtendedLongGauge.class);

    BoundLongGauge bound = ((ExtendedLongGauge) longGauge).bind(Attributes.of(stringKey("q"), "r"));
    bound.set(123);

    testing.waitAndAssertMetrics(
        "test-bound-long-gauge",
        metric ->
            metric
                .hasName("test")
                .hasLongGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(123)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }

  @Test
  void boundDoubleGauge() {
    Meter meter = testing.getOpenTelemetry().getMeter("test-bound-double-gauge");
    DoubleGaugeBuilder gaugeBuilder = meter.gaugeBuilder("test");
    DoubleGauge doubleGauge = gaugeBuilder.build();
    assertThat(doubleGauge).isInstanceOf(ExtendedDoubleGauge.class);

    BoundDoubleGauge bound =
        ((ExtendedDoubleGauge) doubleGauge).bind(Attributes.of(stringKey("q"), "r"));
    bound.set(1.23);

    testing.waitAndAssertMetrics(
        "test-bound-double-gauge",
        metric ->
            metric
                .hasName("test")
                .hasDoubleGaugeSatisfying(
                    gauge ->
                        gauge.hasPointsSatisfying(
                            point ->
                                point
                                    .hasValue(1.23)
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(stringKey("q"), "r")))));
  }
}
