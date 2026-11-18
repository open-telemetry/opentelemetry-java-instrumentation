/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.metrics.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class MetricCallbackRegistrationTest {
  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void sdkRunsCallbackWithNullContextClassLoader() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider provider = SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(provider);
    DoubleGaugeBuilder builder = provider.get("test").gaugeBuilder("test.gauge");

    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    ClassLoader registrationClassLoader = new ClassLoader() {};
    ClassLoader collectionClassLoader = new ClassLoader() {};
    AtomicReference<ClassLoader> callbackClassLoader =
        new AtomicReference<>(registrationClassLoader);
    Consumer<ObservableDoubleMeasurement> callback =
        measurement -> {
          callbackClassLoader.set(Thread.currentThread().getContextClassLoader());
          measurement.record(42);
        };

    thread.setContextClassLoader(registrationClassLoader);
    try {
      ObservableDoubleGauge gauge =
          MetricCallbackRegistration.withoutContextClassLoader(
              () -> builder.buildWithCallback(callback));
      cleanup.deferCleanup(gauge);
      assertThat(thread.getContextClassLoader()).isSameAs(registrationClassLoader);

      thread.setContextClassLoader(collectionClassLoader);
      assertThat(reader.collectAllMetrics())
          .singleElement()
          .satisfies(
              metric ->
                  assertThat(metric)
                      .hasName("test.gauge")
                      .hasDoubleGaugeSatisfying(
                          gaugeData -> gaugeData.hasPointsSatisfying(point -> point.hasValue(42))));
      assertThat(callbackClassLoader.get()).isNull();
      assertThat(thread.getContextClassLoader()).isSameAs(collectionClassLoader);
    } finally {
      thread.setContextClassLoader(originalClassLoader);
    }
  }

  @Test
  void restoresContextClassLoaderWhenRegistrationFails() {
    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    ClassLoader registrationClassLoader = new ClassLoader() {};
    IllegalStateException failure = new IllegalStateException("registration failed");

    thread.setContextClassLoader(registrationClassLoader);
    try {
      assertThatThrownBy(
              () ->
                  MetricCallbackRegistration.withoutContextClassLoader(
                      () -> {
                        assertThat(thread.getContextClassLoader()).isNull();
                        throw failure;
                      }))
          .isSameAs(failure);
      assertThat(thread.getContextClassLoader()).isSameAs(registrationClassLoader);
    } finally {
      thread.setContextClassLoader(originalClassLoader);
    }
  }
}
