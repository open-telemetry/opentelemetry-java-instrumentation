/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FallbackEnabledInstrumentationModuleTest {

  private final InstrumentationModule module =
      new FallbackEnabledInstrumentationModule("first", "second") {
        @Override
        public List<TypeInstrumentation> typeInstrumentations() {
          return emptyList();
        }
      };

  @AfterEach
  void resetConfig() {
    AgentDistributionConfig.resetForTest();
  }

  @ParameterizedTest
  @CsvSource({
    "true,  ,      ,      true",
    "false, ,      ,      true",
    "true,  true,  ,      true",
    "false, true,  ,      true",
    "true,  false, ,      false",
    "false, false, ,      false",
    "true,  ,      true,  true",
    "false, ,      true,  true",
    "true,  ,      false, false",
    "false, ,      false, false",
    "true,  false, true,  false",
    "false, false, true,  false",
    "true,  true,  false, true",
    "false, true,  false, true"
  })
  void flatEnablement(
      boolean commonDefaultEnabled, Boolean first, Boolean second, boolean expected) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.common.default-enabled", true))
        .thenReturn(commonDefaultEnabled);
    when(config.getBoolean("otel.instrumentation.first.enabled")).thenReturn(first);
    when(config.getBoolean("otel.instrumentation.second.enabled")).thenReturn(second);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));

    assertThat(module.defaultEnabled()).isTrue();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    module.instrumentationNames(), module.defaultEnabled(config)))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"false, false, true", "true, false, true", "false, true, false", "true, true, false"})
  void declarativeEnablement(boolean enabled, boolean disabled, boolean expected) {
    AgentDistributionConfig.set(
        new AgentDistributionConfig(
            null,
            null,
            null,
            null,
            new AgentDistributionConfig.InstrumentationConfig(
                false,
                disabled ? singletonList("first") : emptyList(),
                enabled ? singletonList("first") : emptyList())));

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), module.defaultEnabled()))
        .isEqualTo(expected);
  }
}
