/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.v3_1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.reactor.v3_1.operator.ContextPropagationOperatorContextViewInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.reactor.v3_1.operator.ContextPropagationOperatorInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ContextPropagationOperatorContextViewModuleNamesTest {

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
  void namesAreModeDependent() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, null, null);

    if (V3_PREVIEW) {
      assertThat(module.instrumentationNames())
          .containsExactly(
              "reactor",
              "reactor-3.1",
              "reactor-context-propagation-operator",
              "reactor-3.1-context-propagation-operator-context-view");
      assertThat(new ReactorInstrumentationModule().instrumentationNames())
          .containsExactly("reactor", "reactor-3.1", "reactor-3.1-core");
      assertThat(new ContextPropagationOperatorInstrumentationModule().instrumentationNames())
          .containsExactly(
              "reactor",
              "reactor-3.1",
              "reactor-context-propagation-operator",
              "reactor-3.1-context-propagation-operator");
    } else {
      assertThat(module.instrumentationNames())
          .containsExactly("reactor", "reactor-3.4", "reactor-context-propagation-operator");
      assertThat(new ReactorInstrumentationModule().instrumentationNames())
          .containsExactly("reactor", "reactor-3.1");
      assertThat(new ContextPropagationOperatorInstrumentationModule().instrumentationNames())
          .containsExactly("reactor", "reactor-3.1", "reactor-context-propagation-operator");
    }
  }

  @Test
  void unconfiguredModulesUseEitherDefault() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, null, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isTrue();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), false))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    true))
        .isTrue();
  }

  @Test
  void oldVersionNameRemainsEffectiveOutsidePreview() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, null, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(V3_PREVIEW);
  }

  @Test
  void oldVersionNameCanEnableOutsidePreview() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, null, true);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(!V3_PREVIEW);
  }

  @Test
  void baselineVersionNameDoesNotOwnContextViewOutsidePreview() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(true, null, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(V3_PREVIEW);
  }

  @Test
  void oldVersionNameTakesPrecedenceOverOperatorName() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, false, true);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(!V3_PREVIEW);
  }

  @Test
  void oldVersionNameDisablesBeforeOperatorName() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, true, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(V3_PREVIEW);
  }

  @Test
  void oldVersionNameTakesPrecedenceOverContextViewName() {
    ContextPropagationOperatorContextViewInstrumentationModule module =
        module(null, null, true, false);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(V3_PREVIEW);
  }

  @Test
  void operatorNameCanEnableIndependently() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(null, true, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isTrue();
  }

  @Test
  void baselineVersionNameControlsOnlyBaselineModulesOutsidePreview() {
    ContextPropagationOperatorContextViewInstrumentationModule module = module(false, true, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(!V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    true))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), true))
        .isFalse();
  }

  @Test
  void contextViewNameDisablesOnlyContextViewModule() {
    ContextPropagationOperatorContextViewInstrumentationModule module =
        module(null, null, false, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(!V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    true))
        .isTrue();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), true))
        .isTrue();
  }

  @Test
  void contextViewNameEnablesIndependently() {
    ContextPropagationOperatorContextViewInstrumentationModule module =
        module(null, null, true, null);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    false))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), false))
        .isFalse();
  }

  @Test
  void noNewSelectorWarnings() {
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
      ContextPropagationOperatorContextViewInstrumentationModule module = module(null, null, false);
      AgentDistributionConfig.get().isInstrumentationEnabled(module.instrumentationNames(), true);
    } finally {
      logger.removeHandler(handler);
    }

    assertThat(warnings).isEmpty();
  }

  @Test
  void reactorOwnerOverridesNarrowerSelectors() {
    Map<String, Boolean> values = new HashMap<>();
    values.put("reactor", false);
    values.put("reactor-3.1", true);
    values.put("reactor-3.4", true);
    values.put("reactor-context-propagation-operator", true);
    ContextPropagationOperatorContextViewInstrumentationModule module = module(values);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), true))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    true))
        .isFalse();
  }

  @Test
  void previewOnlySelectorsDoNotAffectNormalMode() {
    Map<String, Boolean> values = new HashMap<>();
    values.put("reactor-3.1-core", false);
    values.put("reactor-3.1-context-propagation-operator", false);
    values.put("reactor-3.1-context-propagation-operator-context-view", false);
    ContextPropagationOperatorContextViewInstrumentationModule module = module(values);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(!V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), true))
        .isEqualTo(!V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    true))
        .isEqualTo(!V3_PREVIEW);
  }

  @Test
  void previewOnlySelectorsCanEnableEachHookIndependently() {
    Map<String, Boolean> values = new HashMap<>();
    values.put("reactor-3.1-core", true);
    values.put("reactor-3.1-context-propagation-operator", true);
    values.put("reactor-3.1-context-propagation-operator-context-view", true);
    ContextPropagationOperatorContextViewInstrumentationModule module = module(values);

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), false))
        .isEqualTo(V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ReactorInstrumentationModule().instrumentationNames(), false))
        .isEqualTo(V3_PREVIEW);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new ContextPropagationOperatorInstrumentationModule().instrumentationNames(),
                    false))
        .isEqualTo(V3_PREVIEW);
  }

  private static ContextPropagationOperatorContextViewInstrumentationModule module(
      Boolean sharedEnabled, Boolean operatorEnabled, Boolean legacyEnabled) {
    return module(sharedEnabled, operatorEnabled, null, legacyEnabled);
  }

  private static ContextPropagationOperatorContextViewInstrumentationModule module(
      Boolean sharedEnabled,
      Boolean operatorEnabled,
      Boolean contextViewEnabled,
      Boolean legacyEnabled) {
    Map<String, Boolean> values = new HashMap<>();
    if (sharedEnabled != null) {
      values.put("reactor-3.1", sharedEnabled);
    }
    if (operatorEnabled != null) {
      values.put("reactor-context-propagation-operator", operatorEnabled);
    }
    if (contextViewEnabled != null) {
      values.put("reactor-3.1-context-propagation-operator-context-view", contextViewEnabled);
    }
    if (legacyEnabled != null) {
      values.put("reactor-3.4", legacyEnabled);
    }
    return module(values);
  }

  private static ContextPropagationOperatorContextViewInstrumentationModule module(
      Map<String, Boolean> values) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString()))
        .thenAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              if (!key.startsWith("otel.instrumentation.") || !key.endsWith(".enabled")) {
                return null;
              }
              return values.get(
                  key.substring(
                      "otel.instrumentation.".length(), key.length() - ".enabled".length()));
            });
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));
    return new ContextPropagationOperatorContextViewInstrumentationModule();
  }
}
