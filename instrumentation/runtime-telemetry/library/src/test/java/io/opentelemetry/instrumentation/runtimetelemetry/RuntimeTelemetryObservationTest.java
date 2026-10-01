/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeTelemetryObservationTest {
  @Test
  void configurationRegistersCollectableClassObservers() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    try (SdkMeterProvider provider =
            SdkMeterProvider.builder().registerMetricReader(reader).build();
        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder().setMeterProvider(provider).build();
        RuntimeTelemetry telemetry = Internal.configure(sdk, true)) {
      Set<String> names = Internal.getRegisteredJmxObservers(telemetry);
      assertThat(names)
          .containsExactlyInAnyOrder("jvm.class.count", "jvm.class.loaded", "jvm.class.unloaded");
      assertThat(reader.collectAllMetrics())
          .extracting(metric -> metric.getName())
          .containsAll(names);
      assertThatThrownBy(() -> names.clear()).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Test
  void disabledByDefaultReportsNothing() {
    assertThat(Internal.configure(OpenTelemetry.noop(), false)).isNull();
    assertThat(Internal.getRegisteredJmxObservers(null)).isEmpty();
  }

  @Test
  void declarativeDisableReportsNothing() {
    ExtendedOpenTelemetry sdk = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(sdk.getInstrumentationConfig("runtime_telemetry")).thenReturn(config);
    when(config.get("jfr_metrics/development")).thenReturn(DeclarativeConfigProperties.empty());
    when(config.getBoolean("enabled", true)).thenReturn(false);
    assertThat(Internal.configure(sdk, true)).isNull();
  }

  @Test
  void disabledJmxAndClosedRuntimeReportNothing() {
    try (RuntimeTelemetry telemetry =
        RuntimeTelemetry.builder(OpenTelemetry.noop()).disableAllJmx().build()) {
      assertThat(Internal.getRegisteredJmxObservers(telemetry)).isEmpty();
    }
    RuntimeTelemetry telemetry = RuntimeTelemetry.create(OpenTelemetry.noop());
    telemetry.close();
    assertThat(Internal.getRegisteredJmxObservers(telemetry)).isEmpty();
  }
}
