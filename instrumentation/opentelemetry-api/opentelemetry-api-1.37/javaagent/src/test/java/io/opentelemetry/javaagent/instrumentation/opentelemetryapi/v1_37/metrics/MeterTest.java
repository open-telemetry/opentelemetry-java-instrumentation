/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
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
  void incubatingApiNotAvailable() {
    assertThatThrownBy(
            () -> Class.forName("io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void longHistogram() {
    LongHistogramBuilder builder =
        meter.histogramBuilder("test").ofLongs().setDescription("d").setUnit("u");
    builder.setExplicitBucketBoundariesAdvice(singletonList(10L));

    LongHistogram instrument = builder.build();

    instrument.record(5, Attributes.of(stringKey("test"), "test"));
    instrument.record(6, Attributes.of(stringKey("test"), "test"));

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

    DoubleHistogram instrument = builder.build();

    instrument.record(5.5, Attributes.of(stringKey("test"), "test"));
    instrument.record(6.6, Attributes.of(stringKey("test"), "test"));

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
}
