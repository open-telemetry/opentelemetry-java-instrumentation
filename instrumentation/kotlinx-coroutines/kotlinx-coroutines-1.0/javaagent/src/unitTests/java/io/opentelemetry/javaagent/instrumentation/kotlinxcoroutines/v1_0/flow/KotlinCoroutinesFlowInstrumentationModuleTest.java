/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.KotlinCoroutinesInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;

class KotlinCoroutinesFlowInstrumentationModuleTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  @Test
  void productionNames() {
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(V3_PREVIEW);
    try (MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);

      if (V3_PREVIEW) {
        assertThat(new KotlinCoroutinesFlowInstrumentationModule().instrumentationNames())
            .containsExactly(
                "kotlinx-coroutines", "kotlinx-coroutines-1.0", "kotlinx-coroutines-1.0-flow");
        assertThat(new KotlinCoroutinesInstrumentationModule().instrumentationNames())
            .containsExactly(
                "kotlinx-coroutines", "kotlinx-coroutines-1.0", "kotlinx-coroutines-1.0-core");
      } else {
        assertThat(new KotlinCoroutinesFlowInstrumentationModule().instrumentationNames())
            .containsExactly(
                "kotlinx-coroutines-flow", "kotlinx-coroutines-flow-1.3", "kotlinx-coroutines");
        assertThat(new KotlinCoroutinesInstrumentationModule().instrumentationNames())
            .containsExactly("kotlinx-coroutines", "kotlinx-coroutines-1.0");
      }
    }
  }

  @ParameterizedTest
  @MethodSource("selectorCases")
  void selectorsUseProductionNames(
      Map<String, Boolean> values,
      boolean defaultEnabled,
      boolean normalFlow,
      boolean previewFlow,
      boolean normalCore,
      boolean previewCore) {
    ConfigProperties properties = properties(values);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(V3_PREVIEW);
    try (MockedStatic<AgentDistributionConfig> distribution =
            mockStatic(AgentDistributionConfig.class);
        MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      distribution.when(AgentDistributionConfig::get).thenReturn(config);
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);

      assertThat(
              config.isInstrumentationEnabled(
                  new KotlinCoroutinesFlowInstrumentationModule().instrumentationNames(),
                  defaultEnabled))
          .isEqualTo(V3_PREVIEW ? previewFlow : normalFlow);
      assertThat(
              config.isInstrumentationEnabled(
                  new KotlinCoroutinesInstrumentationModule().instrumentationNames(),
                  defaultEnabled))
          .isEqualTo(V3_PREVIEW ? previewCore : normalCore);
    }
  }

  private static Stream<Arguments> selectorCases() {
    return Stream.of(
        argumentSet("unset defaults off", ImmutableMap.of(), false, false, false, false, false),
        argumentSet("unset defaults on", ImmutableMap.of(), true, true, true, true, true),
        argumentSet(
            "owner enables both",
            ImmutableMap.of(property("kotlinx-coroutines"), true),
            false,
            true,
            true,
            true,
            true),
        argumentSet(
            "version disables core and preview Flow",
            ImmutableMap.of(property("kotlinx-coroutines-1.0"), false),
            true,
            true,
            false,
            false,
            false),
        argumentSet(
            "preview Flow name enables only in preview",
            ImmutableMap.of(property("kotlinx-coroutines-1.0-flow"), true),
            false,
            false,
            true,
            false,
            false),
        argumentSet(
            "preview Flow name disables only in preview",
            ImmutableMap.of(property("kotlinx-coroutines-1.0-flow"), false),
            true,
            true,
            false,
            true,
            true),
        argumentSet(
            "normal Flow name disables Flow only",
            ImmutableMap.of(property("kotlinx-coroutines-flow"), false),
            true,
            false,
            true,
            true,
            true),
        argumentSet(
            "normal Flow version enables only in normal mode",
            ImmutableMap.of(property("kotlinx-coroutines-flow-1.3"), true),
            false,
            true,
            false,
            false,
            false),
        argumentSet(
            "normal Flow name wins over owner",
            ImmutableMap.of(
                property("kotlinx-coroutines"), true, property("kotlinx-coroutines-flow"), false),
            false,
            false,
            true,
            true,
            true),
        argumentSet(
            "normal Flow version wins over owner version",
            ImmutableMap.of(
                property("kotlinx-coroutines-1.0"),
                false,
                property("kotlinx-coroutines-flow-1.3"),
                true),
            true,
            true,
            false,
            false,
            false),
        argumentSet(
            "normal Flow name wins over Flow version",
            ImmutableMap.of(
                property("kotlinx-coroutines-flow"),
                false,
                property("kotlinx-coroutines-flow-1.3"),
                true),
            true,
            false,
            true,
            true,
            true),
        argumentSet(
            "preview owner wins over dedicated Flow name",
            ImmutableMap.of(
                property("kotlinx-coroutines"),
                true,
                property("kotlinx-coroutines-1.0-flow"),
                false),
            true,
            true,
            true,
            true,
            true),
        argumentSet(
            "normal Flow name wins over preview Flow name",
            ImmutableMap.of(
                property("kotlinx-coroutines-flow"),
                false,
                property("kotlinx-coroutines-1.0-flow"),
                true),
            true,
            false,
            true,
            true,
            true),
        argumentSet(
            "preview core selector ignored in normal mode",
            ImmutableMap.of(property("kotlinx-coroutines-1.0-core"), false),
            true,
            true,
            true,
            true,
            false));
  }

  private static ConfigProperties properties(Map<String, Boolean> values) {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString()))
        .thenAnswer(invocation -> values.get(invocation.getArgument(0)));
    return properties;
  }

  private static String property(String name) {
    return "otel.instrumentation." + name + ".enabled";
  }
}
