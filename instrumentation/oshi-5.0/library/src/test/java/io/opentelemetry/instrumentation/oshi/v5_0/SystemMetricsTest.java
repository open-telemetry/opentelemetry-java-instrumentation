/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.v3Preview;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SchemaUrls;
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

  @Override
  @SuppressWarnings("deprecation") // overriding a deprecated abstract method
  protected String scopeName() {
    return "io.opentelemetry.oshi-5.0";
  }

  @Test
  void verifyObservablesAreNotEmpty() {
    assertThat(observables).isNotEmpty();
  }

  @Test
  @SuppressWarnings("deprecation") // caller-owned meters retain legacy conventions
  void callerOwnedMeterKeepsLegacyConventions() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(meterProvider);
    List<AutoCloseable> callerObservers =
        SystemMetrics.registerObservers(meterProvider.get("oshi-caller-meter"));
    callerObservers.forEach(cleanup::deferCleanup);

    assertThat(reader.collectAllMetrics())
        .anySatisfy(
            metric -> {
              assertThat(metric.getName()).isEqualTo("system.network.packets");
              assertThat(metric.getUnit()).isEqualTo("{packets}");
              assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl()).isNull();
            });
  }

  @Test
  void suppliedConfigurationControlsPreviewMode() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();
    cleanup.deferCleanup(meterProvider);
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
    when(openTelemetry.getInstrumentationConfig("common").getBoolean("v3_preview"))
        .thenReturn(!v3Preview());
    SystemMetrics.registerObservers(openTelemetry).forEach(cleanup::deferCleanup);

    assertThat(reader.collectAllMetrics())
        .anySatisfy(
            metric -> {
              assertThat(metric.getName())
                  .isEqualTo(
                      v3Preview() ? "system.network.packets" : "system.network.packet.count");
              assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                  .isEqualTo(
                      v3Preview() ? "https://opentelemetry.io/schemas/1.19.0" : SchemaUrls.V1_44_0);
            });
  }
}
