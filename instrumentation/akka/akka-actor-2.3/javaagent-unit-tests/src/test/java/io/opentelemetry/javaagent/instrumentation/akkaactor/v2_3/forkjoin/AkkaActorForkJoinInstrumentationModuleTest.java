/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkaactor.v2_3.forkjoin;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.akkaactor.v2_3.AkkaActorInstrumentationModule;
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
    AkkaActorForkJoinInstrumentationModule forkJoin = new AkkaActorForkJoinInstrumentationModule();
    AkkaActorInstrumentationModule actor = new AkkaActorInstrumentationModule();
    if (AgentCommonConfig.get().isV3Preview()) {
      assertThat(forkJoin.instrumentationNames())
          .containsExactly("akka-actor", "akka-actor-2.3", "akka-actor-2.3-forkjoin");
      assertThat(actor.instrumentationNames())
          .containsExactly("akka-actor", "akka-actor-2.3", "akka-actor-2.3-core");
    } else {
      assertThat(forkJoin.instrumentationNames())
          .containsExactly(
              "akka-actor-forkjoin",
              "akka-actor-fork-join",
              "akka-actor-forkjoin-2.5",
              "akka-actor-fork-join-2.5",
              "akka-actor");
      assertThat(actor.instrumentationNames()).containsExactly("akka-actor", "akka-actor-2.3");
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
  void originalSelectors(String name) {
    for (boolean enabled : new boolean[] {true, false}) {
      ConfigProperties config = mock(ConfigProperties.class);
      when(config.getBoolean(anyString())).thenReturn(null);
      when(config.getBoolean("otel.instrumentation." + name + ".enabled")).thenReturn(enabled);
      setConfig(config);

      AkkaActorForkJoinInstrumentationModule forkJoin =
          new AkkaActorForkJoinInstrumentationModule();
      assertThat(
              AgentDistributionConfig.get()
                  .isInstrumentationEnabled(forkJoin.instrumentationNames(), !enabled))
          .isEqualTo(AgentCommonConfig.get().isV3Preview() ? !enabled : enabled);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"akka-actor-2.3", "akka-actor-2.3-forkjoin"})
  void previewSelectors(String name) {
    for (boolean enabled : new boolean[] {true, false}) {
      ConfigProperties config = mock(ConfigProperties.class);
      when(config.getBoolean(anyString())).thenReturn(null);
      when(config.getBoolean("otel.instrumentation." + name + ".enabled")).thenReturn(enabled);
      setConfig(config);

      AkkaActorForkJoinInstrumentationModule forkJoin =
          new AkkaActorForkJoinInstrumentationModule();
      assertThat(
              AgentDistributionConfig.get()
                  .isInstrumentationEnabled(forkJoin.instrumentationNames(), !enabled))
          .isEqualTo(AgentCommonConfig.get().isV3Preview() ? enabled : !enabled);
    }
  }

  @Test
  void ownerAndDedicatedSelectorPrecedence() {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.akka-actor.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.akka-actor-forkjoin.enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.akka-actor-2.3-forkjoin.enabled"))
        .thenReturn(false);
    setConfig(config);

    AkkaActorForkJoinInstrumentationModule forkJoin = new AkkaActorForkJoinInstrumentationModule();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(forkJoin.instrumentationNames(), false))
        .isEqualTo(AgentCommonConfig.get().isV3Preview());
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(
                    new AkkaActorInstrumentationModule().instrumentationNames(), false))
        .isTrue();
  }

  @Test
  void originalForkJoinSelectorPrecedesOwner() {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.akka-actor.enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.akka-actor-forkjoin.enabled")).thenReturn(true);
    setConfig(config);

    AkkaActorForkJoinInstrumentationModule forkJoin = new AkkaActorForkJoinInstrumentationModule();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(forkJoin.instrumentationNames(), false))
        .isEqualTo(!AgentCommonConfig.get().isV3Preview());
  }

  @Test
  void defaults() {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    setConfig(config);

    AkkaActorForkJoinInstrumentationModule forkJoin = new AkkaActorForkJoinInstrumentationModule();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(forkJoin.instrumentationNames(), true))
        .isTrue();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(forkJoin.instrumentationNames(), false))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"akka-actor-fork-join", "akka-actor-fork-join-2.5"})
  void originalSpellingWarnings(String name) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation." + name + ".enabled")).thenReturn(true);
    setConfig(config);

    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      new AkkaActorForkJoinInstrumentationModule();
      if (AgentCommonConfig.get().isV3Preview()) {
        verify(handler, never()).publish(any());
      } else {
        ArgumentCaptor<LogRecord> warning = ArgumentCaptor.forClass(LogRecord.class);
        verify(handler).publish(warning.capture());
        assertThat(warning.getValue().getLevel()).isEqualTo(WARNING);
        assertThat(warning.getValue().getParameters())
            .containsExactly(name, name.replace("fork-join", "forkjoin"));
      }
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"akka-actor-forkjoin", "akka-actor-forkjoin-2.5"})
  void originalNamesDoNotWarn(String name) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation." + name + ".enabled")).thenReturn(true);
    setConfig(config);

    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      new AkkaActorForkJoinInstrumentationModule();
      verify(handler, never()).publish(any());
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "akka-actor-forkjoin",
        "akka-actor-fork-join",
        "akka-actor-forkjoin-2.5",
        "akka-actor-fork-join-2.5",
        "akka-actor-2.3",
        "akka-actor-2.3-forkjoin"
      })
  void declarativeSelectors(String name) throws Exception {
    AkkaActorForkJoinInstrumentationModule forkJoin = new AkkaActorForkJoinInstrumentationModule();
    String normalized = name.replace('-', '_');
    boolean recognized =
        AgentCommonConfig.get().isV3Preview()
            ? name.startsWith("akka-actor-2.3")
            : name.startsWith("akka-actor-fork");

    for (boolean enabled : new boolean[] {true, false}) {
      String list = enabled ? "enabled" : "disabled";
      AgentDistributionConfig config =
          new ObjectMapper()
              .readValue(
                  "{\"instrumentation\":{\"" + list + "\":[\"" + normalized + "\"]}}",
                  AgentDistributionConfig.class);
      assertThat(config.isInstrumentationEnabled(forkJoin.instrumentationNames(), !enabled))
          .isEqualTo(recognized ? enabled : !enabled);
    }
  }

  @Test
  void declarativeOwnerPrecedence() throws Exception {
    AgentDistributionConfig config =
        new ObjectMapper()
            .readValue(
                "{\"instrumentation\":{\"enabled\":[\"akka_actor\"],\"disabled\":[\"akka_actor_forkjoin\",\"akka_actor_2.3_forkjoin\"]}}",
                AgentDistributionConfig.class);
    assertThat(
            config.isInstrumentationEnabled(
                new AkkaActorForkJoinInstrumentationModule().instrumentationNames(), false))
        .isEqualTo(AgentCommonConfig.get().isV3Preview());
  }

  private static void setConfig(ConfigProperties config) {
    boolean v3Preview = AgentCommonConfig.get().isV3Preview();
    when(config.getBoolean("otel.instrumentation.common.v3-preview", false)).thenReturn(v3Preview);
    AgentDistributionConfig.resetForTest();
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));
  }
}
