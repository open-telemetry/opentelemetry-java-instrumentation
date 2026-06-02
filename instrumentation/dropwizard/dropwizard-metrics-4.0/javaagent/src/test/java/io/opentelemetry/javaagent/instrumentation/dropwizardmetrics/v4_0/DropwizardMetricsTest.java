/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics.v4_0;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DropwizardMetricsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.dropwizard-metrics-4.0";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void gauge() {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    AtomicLong value = new AtomicLong(42);

    // when
    metricRegistry.gauge("test'gauge", () -> value::get);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("testgauge")
                .hasDoubleGaugeSatisfying(g -> g.hasPointsSatisfying(point -> point.hasValue(42))));

    // when
    metricRegistry.remove("test'gauge");
    testing.clearData();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testgauge", AbstractIterableAssert::isEmpty);
  }

  @Test
  void counter() {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Counter counter = metricRegistry.counter("test@counter");
    counter.inc();
    counter.inc(11);
    counter.dec(5);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("testcounter")
                .hasLongSumSatisfying(
                    sum -> sum.isNotMonotonic().hasPointsSatisfying(point -> point.hasValue(7))));
    testing.clearData();

    // when
    metricRegistry.remove("test@counter");
    counter.inc(123);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testcounter", AbstractIterableAssert::isEmpty);
  }

  @Test
  void histogram() {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Histogram histogram = metricRegistry.histogram("test!histogram");
    histogram.update(12);
    histogram.update(30);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("testhistogram")
                .hasHistogramSatisfying(
                    histogramMetric ->
                        histogramMetric.hasPointsSatisfying(
                            point -> point.hasSum(42).hasCount(2))));
    testing.clearData();

    // when
    metricRegistry.remove("test!histogram");
    histogram.update(100);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testhistogram", AbstractIterableAssert::isEmpty);
  }

  @Test
  void meter() {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Meter meter = metricRegistry.meter("test meter");
    meter.mark();
    meter.mark(11);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("testmeter")
                .hasLongSumSatisfying(
                    sum -> sum.isMonotonic().hasPointsSatisfying(point -> point.hasValue(12))));
    testing.clearData();

    // when
    metricRegistry.remove("test meter");
    meter.mark();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testmeter", AbstractIterableAssert::isEmpty);
  }

  @Test
  @SuppressWarnings("PreferJavaTimeOverload")
  void timer() {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Timer timer = metricRegistry.timer("test#timer");
    timer.update(1, MILLISECONDS);
    timer.update(234_000, NANOSECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("testtimer")
                .hasUnit("ms")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(point -> point.hasSum(1.234).hasCount(2))));
    testing.clearData();

    // when
    metricRegistry.remove("test#timer");
    timer.update(12, SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testtimer", AbstractIterableAssert::isEmpty);
  }
}
