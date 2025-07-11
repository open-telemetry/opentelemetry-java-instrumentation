/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
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
  void incubatingApiNotAvailable() {
    assertThatThrownBy(
            () -> Class.forName("io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder"))
        .isInstanceOf(ClassNotFoundException.class);
  }

  @Test
  void syncLongGauge() throws InterruptedException {
    LongGaugeBuilder builder =
        meter.gaugeBuilder("test").ofLongs().setDescription("d").setUnit("u");

    LongGauge longGauge = builder.build();
    longGauge.set(321);
    longGauge.set(123, Attributes.of(stringKey("test"), "test"));

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
  void syncDoubleGauge() throws InterruptedException {
    DoubleGaugeBuilder builder = meter.gaugeBuilder("test").setDescription("d").setUnit("u");

    DoubleGauge doubleGauge = builder.build();
    doubleGauge.set(3.21);
    doubleGauge.set(1.23, Attributes.of(stringKey("test"), "test"));

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
