/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void conventionsIgnoreSuppliedPreviewConfiguration(boolean preview) {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(meterProvider);
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(preview);
    when(openTelemetry
            .getInstrumentationConfig("oshi")
            .get("experimental_metrics/development")
            .getBoolean("enabled", false))
        .thenReturn(true);
    SystemMetrics.registerObservers(openTelemetry).forEach(cleanup::deferCleanup);

    assertThat(reader.collectAllMetrics())
        .anySatisfy(
            metric -> {
              assertThat(metric.getName()).isEqualTo("system.network.packet.count");
              assertThat(metric.getUnit()).isEqualTo("{packet}");
              assertThat(metric).satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0));
            });
    assertThat(reader.collectAllMetrics())
        .noneMatch(metric -> metric.getName().startsWith("runtime.java."));
  }
}
