/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JarAnalyzerConfigTest {

  @Test
  void disabledByDefault() {
    assertThat(JarAnalyzerConfig.getInstrumentationName(DeclarativeConfigProperties.empty()))
        .isNull();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void enabledSetting(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("enabled", false)).thenReturn(enabled);

    assertThat(JarAnalyzerConfig.getInstrumentationName(config))
        .isEqualTo(enabled ? "io.opentelemetry.runtime-telemetry" : null);
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 0})
  void jarsPerSecond(int jarsPerSecond) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getInt("jars_per_second", -1)).thenReturn(jarsPerSecond);

    assertThat(JarAnalyzerConfig.getJarsPerSecond(config)).isEqualTo(jarsPerSecond);
  }

  @Test
  void defaultJarsPerSecond() {
    assertThat(JarAnalyzerConfig.getJarsPerSecond(DeclarativeConfigProperties.empty()))
        .isEqualTo(10);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, -2})
  void negativeJarsPerSecondUsesDefault(int jarsPerSecond) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    when(config.getInt("jars_per_second", -1)).thenReturn(jarsPerSecond);

    assertThat(JarAnalyzerConfig.getJarsPerSecond(config)).isEqualTo(10);
  }
}
