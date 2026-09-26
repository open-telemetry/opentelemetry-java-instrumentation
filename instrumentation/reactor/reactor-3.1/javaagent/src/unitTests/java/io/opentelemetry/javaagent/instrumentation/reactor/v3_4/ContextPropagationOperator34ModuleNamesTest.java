/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.reactor.v3_1.ReactorInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.reactor.v3_4.operator.ContextPropagationOperator34InstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ContextPropagationOperator34ModuleNamesTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  private MockedStatic<AgentCommonConfig> agentCommonConfig;

  @BeforeEach
  void setUp() {
    CommonConfig config = mock(CommonConfig.class);
    when(config.isV3Preview()).thenReturn(V3_PREVIEW);
    agentCommonConfig = mockStatic(AgentCommonConfig.class);
    agentCommonConfig.when(AgentCommonConfig::get).thenReturn(config);
  }

  @AfterEach
  void tearDown() {
    agentCommonConfig.close();
    AgentDistributionConfig.resetForTest();
  }

  @Test
  void namesKeepCurrentAliasesBeforeDeprecatedName() {
    ContextPropagationOperator34InstrumentationModule module = module(null, null, null);

    if (V3_PREVIEW) {
      assertThat(module.instrumentationNames())
          .containsExactly(
              "reactor",
              "reactor-3.1",
              "reactor-context-propagation-operator",
              "reactor-3.4-context-propagation-operator");
    } else {
      assertThat(module.instrumentationNames())
          .containsExactly(
              "reactor",
              "reactor-3.1",
              "reactor-context-propagation-operator",
              "reactor-3.4-context-propagation-operator",
              "reactor-3.4");
    }
  }

  @Test
  void oldVersionNameRemainsEffectiveOutsidePreview() {
    ContextPropagationOperator34InstrumentationModule module = module(null, null, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(V3_PREVIEW);
  }

  @Test
  void oldVersionNameCanEnableOutsidePreview() {
    ContextPropagationOperator34InstrumentationModule module = module(null, null, true);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(!V3_PREVIEW);
  }

  @Test
  void sharedOwnerNameTakesPrecedenceOverOldVersionName() {
    ContextPropagationOperator34InstrumentationModule module = module(true, null, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isTrue();
  }

  @Test
  void operatorNameTakesPrecedenceOverOldVersionName() {
    ContextPropagationOperator34InstrumentationModule module = module(null, false, true);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isFalse();
  }

  @Test
  void operatorNameCanEnableIndependently() {
    ContextPropagationOperator34InstrumentationModule module = module(null, true, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isTrue();
  }

  @Test
  void sharedOwnerNameControlsBothModules() {
    ContextPropagationOperator34InstrumentationModule module = module(false, true, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), true))
        .isFalse();
  }

  @Test
  void legacySettingWarnsOnlyOutsidePreview() {
    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    List<LogRecord> warnings = new ArrayList<>();
    Handler handler =
        new Handler() {
          @Override
          public void publish(LogRecord record) {
            warnings.add(record);
          }

          @Override
          public void flush() {}

          @Override
          public void close() {}
        };
    logger.addHandler(handler);
    try {
      module(null, null, false);
    } finally {
      logger.removeHandler(handler);
    }

    if (V3_PREVIEW) {
      assertThat(warnings).isEmpty();
    } else {
      assertThat(warnings).hasSize(1);
      assertThat(warnings.get(0).getParameters()).containsExactly("reactor-3.4", "reactor-3.1");
    }
  }

  private static ContextPropagationOperator34InstrumentationModule module(
      Boolean sharedEnabled, Boolean operatorEnabled, Boolean legacyEnabled) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.reactor-3.1.enabled")).thenReturn(sharedEnabled);
    when(config.getBoolean("otel.instrumentation.reactor-context-propagation-operator.enabled"))
        .thenReturn(operatorEnabled);
    when(config.getBoolean("otel.instrumentation.reactor-3.4.enabled")).thenReturn(legacyEnabled);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));
    return new ContextPropagationOperator34InstrumentationModule();
  }
}
