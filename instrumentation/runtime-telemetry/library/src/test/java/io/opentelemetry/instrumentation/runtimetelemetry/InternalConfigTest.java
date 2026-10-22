/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Internal;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InternalConfigTest {

  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void plainOpenTelemetryUsesDefaultEnablement(boolean defaultEnabled) {
    try (RuntimeTelemetry telemetry = Internal.configure(OpenTelemetry.noop(), defaultEnabled)) {
      if (defaultEnabled) {
        assertThat(telemetry).isNotNull();
        assertThat(telemetry.getJfrTelemetry()).isNull();
      } else {
        assertThat(telemetry).isNull();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void configurationOverridesDefaultEnablement(boolean enabled) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(openTelemetry.getInstrumentationConfig("runtime_telemetry")).thenReturn(config);
    when(config.getBoolean("enabled", !enabled)).thenReturn(enabled);
    when(config.get("jfr_metrics/development")).thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.meterBuilder(anyString()))
        .thenAnswer(
            invocation -> testing.getOpenTelemetry().meterBuilder(invocation.getArgument(0)));

    try (RuntimeTelemetry telemetry = Internal.configure(openTelemetry, !enabled)) {
      if (enabled) {
        assertThat(telemetry).isNotNull();
        assertThat(telemetry.getJfrTelemetry()).isNull();
      } else {
        assertThat(telemetry).isNull();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void configuresExperimentalJmxMetrics(boolean experimental) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(openTelemetry.getInstrumentationConfig("runtime_telemetry")).thenReturn(config);
    when(config.getBoolean("enabled", true)).thenReturn(true);
    when(config.getBoolean("emit_experimental_metrics/development", false))
        .thenReturn(experimental);
    when(config.get("jfr_metrics/development")).thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.meterBuilder(anyString()))
        .thenAnswer(
            invocation -> testing.getOpenTelemetry().meterBuilder(invocation.getArgument(0)));

    try (RuntimeTelemetry telemetry = Internal.configure(openTelemetry, true)) {
      assertThat(telemetry).isNotNull();
      assertThat(telemetry.getJfrTelemetry()).isNull();
      assertThat(testing.getMetrics()).extracting(MetricData::getName).contains("jvm.memory.used");
      if (experimental) {
        assertThat(testing.getMetrics())
            .extracting(MetricData::getName)
            .contains("jvm.memory.init");
      } else {
        assertThat(testing.getMetrics())
            .extracting(MetricData::getName)
            .doesNotContain("jvm.memory.init");
      }
    }
  }
}
