/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import io.opentelemetry.extension.incubator.metrics.DoubleGauge;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.extension.incubator.metrics.LongGauge;
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
  void longCounter() {
    LongCounterBuilder builder = meter.counterBuilder("test").setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedLongCounterBuilder.class);
    ExtendedLongCounterBuilder extendedBuilder = (ExtendedLongCounterBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    LongCounter instrument = builder.build();

    instrument.add(5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.add(6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(11)
                                                .hasAttributesSatisfying(
                                                    equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void doubleCounter() {
    DoubleCounterBuilder builder =
        meter.counterBuilder("test").ofDoubles().setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedDoubleCounterBuilder.class);
    ExtendedDoubleCounterBuilder extendedBuilder = (ExtendedDoubleCounterBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    DoubleCounter instrument = builder.build();

    instrument.add(5.5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12.1)
                                                .hasAttributesSatisfying(
                                                    equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void longUpDownCounter() {
    LongUpDownCounterBuilder builder =
        meter.upDownCounterBuilder("test").setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedLongUpDownCounterBuilder.class);
    ExtendedLongUpDownCounterBuilder extendedBuilder = (ExtendedLongUpDownCounterBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    LongUpDownCounter instrument = builder.build();

    instrument.add(5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.add(6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(11)
                                                .hasAttributesSatisfying(
                                                    equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void doubleUpDownCounter() {
    DoubleUpDownCounterBuilder builder =
        meter.upDownCounterBuilder("test").ofDoubles().setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedDoubleUpDownCounterBuilder.class);
    ExtendedDoubleUpDownCounterBuilder extendedBuilder =
        (ExtendedDoubleUpDownCounterBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    DoubleUpDownCounter instrument = builder.build();

    instrument.add(5.5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.add(6.6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasDoubleSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(12.1)
                                                .hasAttributesSatisfying(
                                                    equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void longHistogram() {
    LongHistogramBuilder builder =
        meter.histogramBuilder("test").ofLongs().setDescription("d").setUnit("u");
    builder.setExplicitBucketBoundariesAdvice(singletonList(10L));
    assertThat(builder).isInstanceOf(ExtendedLongHistogramBuilder.class);
    ExtendedLongHistogramBuilder extendedBuilder = (ExtendedLongHistogramBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    LongHistogram instrument = builder.build();

    instrument.record(5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.record(6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(11.0)
                                            .hasBucketBoundaries(10.0)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void doubleHistogram() {
    DoubleHistogramBuilder builder =
        meter.histogramBuilder("test").setDescription("d").setUnit("u");
    builder.setExplicitBucketBoundariesAdvice(singletonList(10.0));
    assertThat(builder).isInstanceOf(ExtendedDoubleHistogramBuilder.class);
    ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    DoubleHistogram instrument = builder.build();

    instrument.record(5.5, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));
    instrument.record(6.6, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasSum(12.1)
                                            .hasBucketBoundaries(10.0)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));
  }

  @Test
  void longGauge() throws InterruptedException {
    LongGaugeBuilder builder =
        meter.gaugeBuilder("test").ofLongs().setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedLongGaugeBuilder.class);
    ExtendedLongGaugeBuilder extendedBuilder = (ExtendedLongGaugeBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    ObservableLongGauge observableGauge =
        builder.buildWithCallback(
            result ->
                result.record(123, Attributes.of(stringKey("test"), "test", stringKey("q"), "r")));

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
                                .build())
                        .hasLongGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(123)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));

    observableGauge.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void syncLongGauge() throws InterruptedException {
    LongGaugeBuilder builder =
        meter.gaugeBuilder("test").ofLongs().setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedLongGaugeBuilder.class);
    ExtendedLongGaugeBuilder extendedBuilder = (ExtendedLongGaugeBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    LongGauge longGauge = extendedBuilder.build();
    longGauge.set(321);
    longGauge.set(123, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasLongGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point -> point.hasValue(321).hasAttributes(Attributes.empty()),
                                    point ->
                                        point
                                            .hasValue(123)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void doubleGauge() throws InterruptedException {
    DoubleGaugeBuilder builder = meter.gaugeBuilder("test").setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedDoubleGaugeBuilder.class);
    ExtendedDoubleGaugeBuilder extendedBuilder = (ExtendedDoubleGaugeBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    ObservableDoubleGauge observableGauge =
        builder.buildWithCallback(
            result ->
                result.record(1.23, Attributes.of(stringKey("test"), "test", stringKey("q"), "r")));

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
                                .build())
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1.23)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));

    observableGauge.close();

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }

  @Test
  void syncDoubleGauge() throws InterruptedException {
    DoubleGaugeBuilder builder = meter.gaugeBuilder("test").setDescription("d").setUnit("u");
    assertThat(builder).isInstanceOf(ExtendedDoubleGaugeBuilder.class);
    ExtendedDoubleGaugeBuilder extendedBuilder = (ExtendedDoubleGaugeBuilder) builder;
    extendedBuilder.setAttributesAdvice(singletonList(stringKey("test")));

    DoubleGauge doubleGauge = extendedBuilder.build();
    doubleGauge.set(3.21);
    doubleGauge.set(1.23, Attributes.of(stringKey("test"), "test", stringKey("q"), "r"));

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
                                .build())
                        .hasDoubleGaugeSatisfying(
                            gauge ->
                                gauge.hasPointsSatisfying(
                                    point -> point.hasValue(3.21).hasAttributes(Attributes.empty()),
                                    point ->
                                        point
                                            .hasValue(1.23)
                                            .hasAttributesSatisfying(
                                                equalTo(stringKey("test"), "test"))))));

    // sleep exporter interval
    Thread.sleep(100);
    testing.clearData();
    Thread.sleep(100);

    testing.waitAndAssertMetrics(instrumentationName, "test", AbstractIterableAssert::isEmpty);
  }
}
