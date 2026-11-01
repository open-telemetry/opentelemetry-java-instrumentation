/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class SystemMetricsTest extends AbstractSystemMetricsTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static List<AutoCloseable> observables;

  @BeforeAll
  static void setUp() {
    observables = SystemMetrics.registerObservers(GlobalOpenTelemetry.get());
    observables.forEach(cleanup::deferAfterAll);
  }

  @Override
  protected void registerMetrics() {}

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Test
  void verifyObservablesAreNotEmpty() {
    assertThat(observables).hasSize(7);
  }

  @Test
  void closingObserversStopsCollection() throws Exception {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(meterProvider);
    List<AutoCloseable> callerObservers =
        SystemMetrics.registerObservers(
            OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build());
    try {
      assertThat(reader.collectAllMetrics()).isNotEmpty();
    } finally {
      for (AutoCloseable observer : callerObservers) {
        observer.close();
      }
    }
    assertThat(reader.collectAllMetrics()).isEmpty();
  }
}
