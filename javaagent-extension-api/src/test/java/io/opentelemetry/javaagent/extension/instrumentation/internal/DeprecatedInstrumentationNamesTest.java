/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames.expandDeprecatedNames;
import static java.util.Arrays.asList;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.LinkedHashSet;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class DeprecatedInstrumentationNamesTest {

  private static final Logger logger =
      Logger.getLogger(DeprecatedInstrumentationNames.class.getName());

  @Test
  void legacyAliasesFallBackAndWarnEvenWhenReplacementIsPresent() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-flow.enabled"))
        .thenReturn(false);
    when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-flow-1.3.enabled"))
        .thenReturn(true);
    when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-1.0.enabled"))
        .thenReturn(true);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
    CommonConfig commonConfig = mock(CommonConfig.class);
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try (MockedStatic<AgentDistributionConfig> distribution =
            mockStatic(AgentDistributionConfig.class);
        MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      distribution.when(AgentDistributionConfig::get).thenReturn(config);
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);

      String[] names =
          expandDeprecatedNames(
              "kotlinx-coroutines-1.0",
              "kotlinx-coroutines-1.0-flow",
              "kotlinx-coroutines|deprecated:kotlinx-coroutines-flow",
              "kotlinx-coroutines-1.0|deprecated:kotlinx-coroutines-flow-1.3");
      LinkedHashSet<String> ordered = new LinkedHashSet<>();
      ordered.add("kotlinx-coroutines");
      ordered.addAll(asList(names));
      assertThat(ordered)
          .containsExactly(
              "kotlinx-coroutines",
              "kotlinx-coroutines-1.0",
              "kotlinx-coroutines-1.0-flow",
              "kotlinx-coroutines-flow",
              "kotlinx-coroutines-flow-1.3");
      assertThat(config.isInstrumentationEnabled(asList(names), false)).isTrue();
      when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-1.0.enabled"))
          .thenReturn(null);
      assertThat(config.isInstrumentationEnabled(asList(names), true)).isFalse();

      ArgumentCaptor<LogRecord> records = ArgumentCaptor.forClass(LogRecord.class);
      verify(handler, times(2)).publish(records.capture());
      assertThat(records.getAllValues())
          .extracting(LogRecord::getLevel)
          .containsExactly(WARNING, WARNING);
      assertThat(records.getAllValues().get(0).getParameters())
          .containsExactly("kotlinx-coroutines-flow", "kotlinx-coroutines");
      assertThat(records.getAllValues().get(1).getParameters())
          .containsExactly("kotlinx-coroutines-flow-1.3", "kotlinx-coroutines-1.0");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void yamlAliasesFallBackAndAreIgnoredInPreview() {
    AgentDistributionConfig config =
        new AgentDistributionConfig(
            null,
            null,
            null,
            null,
            null,
            new AgentDistributionConfig.InstrumentationConfig(
                null, asList("kotlinx_coroutines_flow", "kotlinx_coroutines_flow_1.3"), null));
    CommonConfig commonConfig = mock(CommonConfig.class);
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try (MockedStatic<AgentDistributionConfig> distribution =
            mockStatic(AgentDistributionConfig.class);
        MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      distribution.when(AgentDistributionConfig::get).thenReturn(config);
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);

      String[] names =
          expandDeprecatedNames(
              "kotlinx-coroutines-1.0",
              "kotlinx-coroutines-1.0-flow",
              "kotlinx-coroutines|deprecated:kotlinx-coroutines-flow",
              "kotlinx-coroutines-1.0|deprecated:kotlinx-coroutines-flow-1.3");
      assertThat(config.isInstrumentationEnabled(asList(names), true)).isFalse();
      verify(handler, times(2)).publish(any(LogRecord.class));

      when(commonConfig.isV3Preview()).thenReturn(true);
      names =
          expandDeprecatedNames(
              "kotlinx-coroutines-1.0",
              "kotlinx-coroutines-1.0-flow",
              "kotlinx-coroutines|deprecated:kotlinx-coroutines-flow",
              "kotlinx-coroutines-1.0|deprecated:kotlinx-coroutines-flow-1.3");
      assertThat(config.isInstrumentationEnabled(asList(names), true)).isTrue();
      verify(handler, times(2)).publish(any(LogRecord.class));
    } finally {
      logger.removeHandler(handler);
    }
  }

  @Test
  void previewIgnoresLegacyAliasesWithoutWarning() {
    ConfigProperties properties = mock(ConfigProperties.class);
    when(properties.getBoolean(anyString())).thenReturn(null);
    when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-flow.enabled"))
        .thenReturn(false);
    when(properties.getBoolean("otel.instrumentation.kotlinx-coroutines-flow-1.3.enabled"))
        .thenReturn(false);
    AgentDistributionConfig config = AgentDistributionConfig.fromConfigProperties(properties);
    CommonConfig commonConfig = mock(CommonConfig.class);
    when(commonConfig.isV3Preview()).thenReturn(true);
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try (MockedStatic<AgentDistributionConfig> distribution =
            mockStatic(AgentDistributionConfig.class);
        MockedStatic<AgentCommonConfig> common = mockStatic(AgentCommonConfig.class)) {
      distribution.when(AgentDistributionConfig::get).thenReturn(config);
      common.when(AgentCommonConfig::get).thenReturn(commonConfig);

      String[] names =
          expandDeprecatedNames(
              "kotlinx-coroutines-1.0",
              "kotlinx-coroutines-1.0-flow",
              "kotlinx-coroutines|deprecated:kotlinx-coroutines-flow",
              "kotlinx-coroutines-1.0|deprecated:kotlinx-coroutines-flow-1.3");
      LinkedHashSet<String> ordered = new LinkedHashSet<>();
      ordered.add("kotlinx-coroutines");
      ordered.addAll(asList(names));
      assertThat(ordered)
          .containsExactly(
              "kotlinx-coroutines", "kotlinx-coroutines-1.0", "kotlinx-coroutines-1.0-flow");
      assertThat(config.isInstrumentationEnabled(asList(names), true)).isTrue();
      verifyNoInteractions(handler);
    } finally {
      logger.removeHandler(handler);
    }
  }
}
