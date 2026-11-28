/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.Internal;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ObservableCallbackLifecycleTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void closeUnregistersCallbacksWhenMetersAreVisible() {
    verifyCloseUnregistersCallbacks(false);
  }

  @Test
  void closeUnregistersCallbacksWhenMetersAreHidden() {
    verifyCloseUnregistersCallbacks(true);
  }

  @Test
  void closeDisabledStillAllowsMeterRemoval() {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry());
    Internal.setCloseDisabled(builder, true);
    MeterRegistry registry = builder.build();
    AtomicLong value = new AtomicLong(42);
    Gauge gauge =
        Gauge.builder("test.registry.shared", value, AtomicLong::doubleValue).register(registry);

    registry.close();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.registry.shared")
                .hasDoubleGaugeSatisfying(
                    data -> data.hasPointsSatisfying(point -> point.hasValue(42))));

    registry.remove(gauge);
    testing.clearData();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.registry.shared", AbstractIterableAssert::isEmpty);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void removeUnregistersTimeGaugeCallback(boolean hidden) {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry());
    Internal.setMetersHiddenFromSearch(builder, hidden);
    MeterRegistry registry = builder.build();
    AtomicLong value = new AtomicLong(42);
    TimeGauge timeGauge =
        TimeGauge.builder("test.registry.time.remove", value, MILLISECONDS, AtomicLong::doubleValue)
            .register(registry);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.registry.time.remove")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.042))));

    registry.remove(timeGauge);
    testing.clearData();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.registry.time.remove", AbstractIterableAssert::isEmpty);
    registry.close();
  }

  private static void verifyCloseUnregistersCallbacks(boolean hidden) {
    OpenTelemetryMeterRegistryBuilder builder =
        OpenTelemetryMeterRegistry.builder(testing.getOpenTelemetry());
    Internal.setMetersHiddenFromSearch(builder, hidden);
    MeterRegistry registry = builder.build();
    AtomicLong value = new AtomicLong(42);
    Gauge.builder("test.registry.close", value, AtomicLong::doubleValue).register(registry);
    TimeGauge.builder("test.registry.time.close", value, MILLISECONDS, AtomicLong::doubleValue)
        .register(registry);

    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.registry.close")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(42))));
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME,
        metric ->
            metric
                .hasName("test.registry.time.close")
                .hasUnit("seconds")
                .hasDoubleGaugeSatisfying(
                    gauge -> gauge.hasPointsSatisfying(point -> point.hasValue(0.042))));
    if (hidden) {
      assertThat(registry.getMeters()).isEmpty();
    }

    testing.clearData();
    registry.close();
    registry.close();
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.registry.close", AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        INSTRUMENTATION_NAME, "test.registry.time.close", AbstractIterableAssert::isEmpty);
  }
}
