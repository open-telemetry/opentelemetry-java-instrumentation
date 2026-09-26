/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akka.actor.forkjoin.v2_5;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AkkaActorForkJoinInstrumentationModuleTest {

  private AgentDistributionConfig originalConfig;

  @BeforeAll
  static void configurePreview() {
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, delegatesTo(empty()));
    when(commonConfig.getBoolean("v3_preview", false))
        .thenReturn(Boolean.getBoolean("otel.instrumentation.common.v3-preview"));
    ConfigProvider provider = mock(ConfigProvider.class);
    when(provider.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(provider.getGeneralInstrumentationConfig()).thenReturn(empty());
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getConfigProvider()).thenReturn(provider);
    GlobalOpenTelemetry.set(openTelemetry);
  }

  @BeforeEach
  void saveConfig() {
    originalConfig = AgentDistributionConfig.get();
  }

  @AfterEach
  void restoreConfig() {
    AgentDistributionConfig.resetForTest();
    AgentDistributionConfig.set(originalConfig);
  }

  @Test
  void names() {
    assertThat(AgentCommonConfig.get().isV3Preview())
        .isEqualTo(Boolean.getBoolean("otel.instrumentation.common.v3-preview"));
    AkkaActorForkJoinInstrumentationModule module = new AkkaActorForkJoinInstrumentationModule();
    if (AgentCommonConfig.get().isV3Preview()) {
      assertThat(module.instrumentationNames())
          .containsExactly("akka-actor", "akka-actor-2.3", "akka-actor-2.3-forkjoin");
    } else {
      assertThat(module.instrumentationNames())
          .containsExactly(
              "akka-actor",
              "akka-actor-2.3",
              "akka-actor-2.3-forkjoin",
              "akka-actor-forkjoin",
              "akka-actor-fork-join",
              "akka-actor-forkjoin-2.5",
              "akka-actor-fork-join-2.5");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "akka-actor-forkjoin",
        "akka-actor-fork-join",
        "akka-actor-forkjoin-2.5",
        "akka-actor-fork-join-2.5"
      })
  void deprecatedAlias(String alias) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation." + alias + ".enabled")).thenReturn(true);
    setConfig(config);

    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      AkkaActorForkJoinInstrumentationModule module = new AkkaActorForkJoinInstrumentationModule();
      if (!AgentCommonConfig.get().isV3Preview()) {
        assertThat(module.instrumentationNames()).contains(alias);
        assertThat(AgentDistributionConfig.get().isInstrumentationEnabled(alias, false)).isTrue();
        assertThat(AgentDistributionConfig.get().isInstrumentationEnabled("akka-actor", true))
            .isTrue();
      }
      assertThat(
              AgentDistributionConfig.get()
                  .isInstrumentationEnabled(module.instrumentationNames(), false))
          .isEqualTo(!AgentCommonConfig.get().isV3Preview());
      if (AgentCommonConfig.get().isV3Preview()) {
        verify(handler, never()).publish(any());
      } else {
        ArgumentCaptor<LogRecord> warning = ArgumentCaptor.forClass(LogRecord.class);
        verify(handler).publish(warning.capture());
        assertThat(warning.getValue().getLevel()).isEqualTo(WARNING);
        assertThat(warning.getValue().getParameters()).containsExactly(alias, "akka-actor-2.3");
      }
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"akka-actor", "akka-actor-2.3", "akka-actor-2.3-forkjoin"})
  void replacementNamesTakePrecedence(String replacement) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation." + replacement + ".enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.akka-actor-forkjoin.enabled")).thenReturn(true);
    setConfig(config);

    AkkaActorForkJoinInstrumentationModule module = new AkkaActorForkJoinInstrumentationModule();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isFalse();
  }

  private static void setConfig(ConfigProperties config) {
    boolean v3Preview = AgentCommonConfig.get().isV3Preview();
    when(config.getBoolean("otel.instrumentation.common.v3-preview", false)).thenReturn(v3Preview);
    AgentDistributionConfig.resetForTest();
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));
  }
}
