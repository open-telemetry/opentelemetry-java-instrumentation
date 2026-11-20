/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Internal;
import io.opentelemetry.instrumentation.test.utils.GcUtils;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ObservableCallbackLifecycleTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension
  static final MicrometerTestingExtension micrometerExtension =
      new MicrometerTestingExtension(testing);

  @Test
  void closeUnregistersCallbacksWhenMetersAreVisible() {
    verifyCloseUnregistersCallbacks(false);
  }

  @Test
  void closeUnregistersCallbacksWhenMetersAreHidden() {
    verifyCloseUnregistersCallbacks(true);
  }

  @Test
  void closeReleasesRegistrationClassLoaderWhileRegistryRemainsReachable() throws Exception {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(testing.getOpenTelemetry());
    AtomicLong value = new AtomicLong(42);
    ClassLoader registrationClassLoader = new ClassLoader() {};
    WeakReference<ClassLoader> registrationClassLoaderRef =
        new WeakReference<>(registrationClassLoader);

    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    thread.setContextClassLoader(registrationClassLoader);
    try {
      Gauge.builder("test.registry.close.loader", value, AtomicLong::doubleValue)
          .register(registry);
    } finally {
      thread.setContextClassLoader(originalClassLoader);
    }

    registry.close();
    registrationClassLoader = null;

    GcUtils.awaitGc(registrationClassLoaderRef, Duration.ofSeconds(10));
    assertThat(registrationClassLoaderRef.get()).isNull();
    assertThat(registry).isNotNull();
  }

  @Test
  void customMeterRetainsMeasurementsUntilRemoval() throws Exception {
    MeterRegistry registry = OpenTelemetryMeterRegistry.create(testing.getOpenTelemetry());
    AtomicReference<Double> value = new AtomicReference<>(123.0);
    List<Measurement> constructionMeasurements = new ArrayList<>();
    Measurement measurement = new Measurement(value::get, Statistic.VALUE);
    constructionMeasurements.add(measurement);
    WeakReference<Measurement> measurementRef = new WeakReference<>(measurement);
    Meter meter =
        Meter.builder("test.custom.measurement", Meter.Type.OTHER, constructionMeasurements)
            .register(registry);

    constructionMeasurements.clear();
    measurement = null;
    GcUtils.awaitGc(Duration.ofSeconds(10));
    assertThat(measurementRef.get()).isNotNull();

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.custom.measurement.value")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(123))));

    testing.clearData();
    registry.close();
    GcUtils.awaitGc(measurementRef, Duration.ofSeconds(10));
    assertThat(measurementRef.get()).isNull();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.custom.measurement.value", AbstractIterableAssert::isEmpty);
    assertThat(meter).isNotNull();
  }

  private static void verifyCloseUnregistersCallbacks(boolean hidden) {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry());
    Internal.setMetersHiddenFromSearch(builder, hidden);
    MeterRegistry registry = builder.build();
    AtomicLong value = new AtomicLong(42);
    Gauge.builder("test.registry.close", value, AtomicLong::doubleValue).register(registry);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.registry.close")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42))));
    if (hidden) {
      assertThat(registry.getMeters()).isEmpty();
    }

    testing.clearData();
    registry.close();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.registry.close", AbstractIterableAssert::isEmpty);
  }
}
