/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DropwizardMetricsTest {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.dropwizard-metrics-4.0";

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void gauge() throws InterruptedException {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    AtomicLong value = new AtomicLong(42);

    // when
    metricRegistry.gauge("test.gauge", () -> value::get);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.gauge",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasDoubleGaugeSatisfying(
                            g -> g.hasPointsSatisfying(point -> point.hasValue(42)))));

    // when
    metricRegistry.remove("test.gauge");
    Thread.sleep(100); // give time for any inflight metric export to be received
    testing.clearData();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.gauge", AbstractIterableAssert::isEmpty);
  }

  @Test
  void counter() throws InterruptedException {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Counter counter = metricRegistry.counter("test.counter");
    counter.inc();
    counter.inc(11);
    counter.dec(5);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.counter",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(point -> point.hasValue(7)))));
    testing.clearData();

    // when
    metricRegistry.remove("test.counter");
    counter.inc(123);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.counter", AbstractIterableAssert::isEmpty);
  }

  @Test
  void histogram() throws InterruptedException {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Histogram histogram = metricRegistry.histogram("test.histogram");
    histogram.update(12);
    histogram.update(30);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.histogram",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasHistogramSatisfying(
                            histogramMetric ->
                                histogramMetric.hasPointsSatisfying(
                                    point -> point.hasSum(42).hasCount(2)))));
    testing.clearData();

    // when
    metricRegistry.remove("test.histogram");
    histogram.update(100);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.histogram", AbstractIterableAssert::isEmpty);
  }

  @Test
  void meter() throws InterruptedException {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Meter meter = metricRegistry.meter("test.meter");
    meter.mark();
    meter.mark(11);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.meter",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isMonotonic()
                                    .hasPointsSatisfying(point -> point.hasValue(12)))));
    testing.clearData();

    // when
    metricRegistry.remove("test.meter");
    meter.mark();

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.meter", AbstractIterableAssert::isEmpty);
  }

  @Test
  @SuppressWarnings("PreferJavaTimeOverload")
  void timer() throws InterruptedException {
    // given
    MetricRegistry metricRegistry = new MetricRegistry();

    // when
    Timer timer = metricRegistry.timer("test.timer");
    timer.update(1, TimeUnit.MILLISECONDS);
    timer.update(234_000, TimeUnit.NANOSECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "test.timer",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point -> point.hasSum(1.234).hasCount(2)))));
    testing.clearData();

    // when
    metricRegistry.remove("test.timer");
    timer.update(12, TimeUnit.SECONDS);

    // then
    Thread.sleep(100); // interval of the test metrics exporter
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.timer", AbstractIterableAssert::isEmpty);
  }
}
