/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class RatpackInstrumentationNamesTest {

  private MockedStatic<AgentCommonConfig> agentCommonConfig;
  private CommonConfig commonConfig;

  @BeforeEach
  void setUp() {
    commonConfig = mock(CommonConfig.class);
    agentCommonConfig = mockStatic(AgentCommonConfig.class);
    agentCommonConfig.when(AgentCommonConfig::get).thenReturn(commonConfig);
  }

  @AfterEach
  void tearDown() {
    agentCommonConfig.close();
    AgentDistributionConfig.resetForTest();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void namesAndMuzzleSelectors(boolean v3Preview) {
    when(commonConfig.isV3Preview()).thenReturn(v3Preview);
    InstrumentationModule older =
        new io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackInstrumentationModule();
    InstrumentationModule newer =
        new io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.RatpackInstrumentationModule();

    assertThat(older.instrumentationNames())
        .containsExactly("ratpack", "ratpack-1.4", "ratpack-1.4-muzzle");
    if (v3Preview) {
      assertThat(newer.instrumentationNames())
          .containsExactly("ratpack", "ratpack-1.4", "ratpack-1.7-muzzle");
    } else {
      assertThat(newer.instrumentationNames())
          .containsExactly("ratpack", "ratpack-1.4", "ratpack-1.7", "ratpack-1.7-muzzle");
    }
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "false, true,  true,  true,  false, false, false",
        "true,  false, false, false, true,  true,  true",
        "null,  false, true,  true,  false, false, false",
        "null,  true,  false, false, true,  true,  true",
        "null,  null,  false, true,  true,  false, true",
        "null,  null,  true,  false, false, true,  false"
      })
  void aliasPrecedence(
      Boolean common,
      Boolean owner,
      Boolean legacy,
      boolean defaultEnabled,
      boolean expectedOlder,
      boolean expectedNewer,
      boolean expectedNewerPreview) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.ratpack.enabled")).thenReturn(common);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4.enabled")).thenReturn(owner);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7.enabled")).thenReturn(legacy);
    AgentDistributionConfig distribution = AgentDistributionConfig.fromConfigProperties(config);

    InstrumentationModule older =
        new io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackInstrumentationModule();
    assertThat(distribution.isInstrumentationEnabled(older.instrumentationNames(), defaultEnabled))
        .isEqualTo(expectedOlder);
    for (boolean preview : new boolean[] {false, true}) {
      when(commonConfig.isV3Preview()).thenReturn(preview);
      InstrumentationModule newer =
          new io.opentelemetry.javaagent.instrumentation.ratpack.v1_7
              .RatpackInstrumentationModule();
      assertThat(
              distribution.isInstrumentationEnabled(newer.instrumentationNames(), defaultEnabled))
          .isEqualTo(preview ? expectedNewerPreview : expectedNewer);
    }
  }

  @Test
  void muzzleSelectorsAreIndependent() {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4-muzzle.enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7-muzzle.enabled")).thenReturn(true);
    AgentDistributionConfig distribution = AgentDistributionConfig.fromConfigProperties(config);

    InstrumentationModule older =
        new io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackInstrumentationModule();
    InstrumentationModule newer =
        new io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.RatpackInstrumentationModule();
    assertThat(distribution.isInstrumentationEnabled(older.instrumentationNames(), true)).isFalse();
    assertThat(distribution.isInstrumentationEnabled(newer.instrumentationNames(), false)).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      nullValues = "null",
      value = {
        "false, true,  null,  true",
        "false, true,  false, true",
        "false, false, null,  true",
        "false, null,  null,  false",
        "true,  true,  null,  false",
        "true,  false, null,  false"
      })
  void warnsOnlyForLegacyAliasOutsidePreview(
      boolean v3Preview, Boolean legacy, Boolean owner, boolean expectedWarning) {
    when(commonConfig.isV3Preview()).thenReturn(v3Preview);
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7.enabled")).thenReturn(legacy);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4.enabled")).thenReturn(owner);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));

    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      new io.opentelemetry.javaagent.instrumentation.ratpack.v1_7.RatpackInstrumentationModule();
    } finally {
      logger.removeHandler(handler);
    }

    if (expectedWarning) {
      ArgumentCaptor<LogRecord> record = ArgumentCaptor.forClass(LogRecord.class);
      verify(handler).publish(record.capture());
      assertThat(record.getValue().getLevel()).isEqualTo(WARNING);
      assertThat(record.getValue().getParameters()).containsExactly("ratpack-1.7", "ratpack-1.4");
    } else {
      verify(handler, never()).publish(any());
    }
  }
}
