/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractNamingConventionTest {

  protected abstract InstrumentationExtension testing();

  protected static NamingConvention namingConvention() {
    return new NamingConvention() {
      @Override
      public String name(String name, Meter.Type type, String baseUnit) {
        return "test." + name;
      }

      @Override
      public String tagKey(String key) {
        return "test." + key;
      }

      @Override
      public String tagValue(String value) {
        return "test." + value;
      }
    };
  }

  final AtomicLong num = new AtomicLong(42);

  @Test
  void renameCounter() {
    // given
    Counter counter = Metrics.counter("renamedCounter", "tag", "value");

    // when
    counter.increment();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedCounter",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameDistributionSummary() {
    // given
    DistributionSummary summary = Metrics.summary("renamedSummary", "tag", "value");

    // when
    summary.record(42);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedSummary",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedSummary.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameFunctionCounter() {
    // given
    Metrics.more().counter("renamedFunctionCounter", Tags.of("tag", "value"), num);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedFunctionCounter",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameFunctionTimer() {
    // given
    Metrics.more()
        .timer(
            "renamedFunctionTimer",
            Tags.of("tag", "value"),
            num,
            AtomicLong::longValue,
            AtomicLong::doubleValue,
            TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedFunctionTimer.count",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedFunctionTimer.sum",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameGauge() {
    // given
    Metrics.gauge("renamedGauge", Tags.of("tag", "value"), num);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedGauge",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameLongTaskTimer() {
    // given
    LongTaskTimer timer = Metrics.more().longTaskTimer("renamedLongTaskTimer", "tag", "value");

    // when
    timer.start().stop();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedLongTaskTimer.active",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasLongSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedLongTaskTimer.duration",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleSumSatisfying(
                                sum ->
                                    sum.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }

  @Test
  void renameTimer() {
    // given
    Timer timer = Metrics.timer("renamedTimer", "tag", "value");

    // when
    timer.record(10, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "test.renamedTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                attributeEntry("test.tag", "test.value"))))));
  }
}
