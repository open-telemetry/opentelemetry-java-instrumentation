/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.dropwizardmetrics.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                .hasDoubleGaugePointsSatisfying(point -> point.hasValue(42)));

    // when
    metricRegistry.remove("test'gauge");
    testing.clearData();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testgauge", AbstractIterableAssert::isEmpty);
  }

  @Test
  void gaugeDoesNotRetainApplicationClassLoader()
      throws IOException, ReflectiveOperationException, InterruptedException, TimeoutException {
    WeakReference<ClassLoader> classLoaderRef = registerGaugeFromDisposableClassLoader();

    GcUtils.awaitGc(classLoaderRef, Duration.ofSeconds(10));
    assertThat(classLoaderRef.get()).isNull();

    testing.clearData();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.gc.disposable.gauge", AbstractIterableAssert::isEmpty);
  }

  private WeakReference<ClassLoader> registerGaugeFromDisposableClassLoader()
      throws IOException, ReflectiveOperationException {
    MetricRegistry metricRegistry = new MetricRegistry();
    URL testClasses = getClass().getProtectionDomain().getCodeSource().getLocation();
    String gaugeClassName = getClass().getName() + "$DisposableGauge";
    URLClassLoader appClassLoader =
        new URLClassLoader(new URL[] {testClasses}, getClass().getClassLoader()) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals(gaugeClassName)) {
              return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
              Class<?> loadedClass = findLoadedClass(name);
              if (loadedClass == null) {
                loadedClass = findClass(name);
              }
              if (resolve) {
                resolveClass(loadedClass);
              }
              return loadedClass;
            }
          }
        };
    try {
      Constructor<?> gaugeConstructor =
          appClassLoader.loadClass(gaugeClassName).getDeclaredConstructor();
      gaugeConstructor.setAccessible(true);
      Gauge<?> gauge = (Gauge<?>) gaugeConstructor.newInstance();
      Thread thread = Thread.currentThread();
      ClassLoader previousContextClassLoader = thread.getContextClassLoader();
      try {
        thread.setContextClassLoader(getClass().getClassLoader());
        metricRegistry.register("test.gc.disposable.gauge", gauge);
      } finally {
        thread.setContextClassLoader(previousContextClassLoader);
      }

      testing.waitAndAssertMetrics(
          INSTRUMENTATION_NAME,
          metric ->
              metric
                  .hasName("test.gc.disposable.gauge")
                  .hasDoubleGaugeSatisfying(
                      result -> result.hasPointsSatisfying(point -> point.hasValue(42))));
      // Keep the registry and its listener alive through the gauge assertion.
      assertThat(metricRegistry.getGauges()).containsEntry("test.gc.disposable.gauge", gauge);
      return new WeakReference<>(appClassLoader);
    } finally {
      appClassLoader.close();
    }
  }

  public static class DisposableGauge implements Gauge<Integer> {

    @Override
    public Integer getValue() {
      return 42;
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void sameGaugeNameInSeparateRegistriesKeepsOtherRegistrationWhenRemoved(boolean removeFirst) {
    verifySeparateRegistryGaugeRemoval(removeFirst);
  }

  private static void verifySeparateRegistryGaugeRemoval(boolean removeFirst) {
    MetricRegistry firstRegistry = new MetricRegistry();
    MetricRegistry secondRegistry = new MetricRegistry();
    AtomicLong firstValue = new AtomicLong(41);
    AtomicLong secondValue = new AtomicLong(42);
    firstRegistry.gauge("test.multi.registry.gauge", () -> firstValue::get);
    secondRegistry.gauge("test.multi.registry.gauge", () -> secondValue::get);

    MetricRegistry removedRegistry = removeFirst ? firstRegistry : secondRegistry;
    MetricRegistry remainingRegistry = removeFirst ? secondRegistry : firstRegistry;
    AtomicLong remainingValue = removeFirst ? secondValue : firstValue;
    double expectedValue = (double) remainingValue.get();
    removedRegistry.remove("test.multi.registry.gauge");
    testing.clearData();

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.multi.registry.gauge")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(expectedValue))));

    remainingRegistry.remove("test.multi.registry.gauge");
    testing.clearData();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.multi.registry.gauge", AbstractIterableAssert::isEmpty);
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

    // when
    metricRegistry.remove("test@counter");
    counter.inc(123);
    testing.clearData();

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        "testcounter",
        metrics ->
            metrics
                .isNotEmpty()
                .allSatisfy(metric ->
                    assertThat(metric)
                        .hasLongSumSatisfying(sum ->
                            sum.isNotMonotonic().hasPointsSatisfying(point -> point.hasValue(7)))));
  }

  @Test
  void removingSameNamedCounterFromAnotherRegistryKeepsRecordingAssociation() {
    MetricRegistry removedRegistry = new MetricRegistry();
    MetricRegistry remainingRegistry = new MetricRegistry();
    removedRegistry.counter("test.multi.registry.counter");
    Counter remainingCounter = remainingRegistry.counter("test.multi.registry.counter");

    removedRegistry.remove("test.multi.registry.counter");
    remainingCounter.inc(5);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.multi.registry.counter")
                .hasLongSumSatisfying(
                    sum -> sum.isNotMonotonic().hasPointsSatisfying(point -> point.hasValue(5))));
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
                .hasHistogramPointsSatisfying(point -> point.hasSum(42).hasCount(2)));
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
                .hasHistogramPointsSatisfying(point -> point.hasSum(1.234).hasCount(2)));
    testing.clearData();

    // when
    metricRegistry.remove("test#timer");
    timer.update(12, SECONDS);

    // then
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "testtimer", AbstractIterableAssert::isEmpty);
  }
}
