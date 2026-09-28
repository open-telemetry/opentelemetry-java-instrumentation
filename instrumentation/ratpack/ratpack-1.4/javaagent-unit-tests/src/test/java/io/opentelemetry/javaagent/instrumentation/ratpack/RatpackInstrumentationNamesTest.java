/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.RatpackInstrumentationModule;
import io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.httpclient.RatpackHttpClientInstrumentationModule;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.logging.Handler;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
    InstrumentationModule older = new RatpackInstrumentationModule();
    InstrumentationModule newer = new RatpackHttpClientInstrumentationModule();

    if (v3Preview) {
      assertThat(older.instrumentationNames())
          .containsExactly("ratpack", "ratpack-1.4", "ratpack-1.4-core");
      assertThat(newer.instrumentationNames())
          .containsExactly("ratpack", "ratpack-1.4", "ratpack-1.4-http-client");
    } else {
      assertThat(older.instrumentationNames()).containsExactly("ratpack", "ratpack-1.4");
      assertThat(newer.instrumentationNames()).containsExactly("ratpack", "ratpack-1.7");
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "ratpack, true, true, true, true",
        "ratpack-1.4, true, false, true, true",
        "ratpack-1.7, false, true, false, false",
        "ratpack-1.4-core, false, false, true, false",
        "ratpack-1.4-http-client, false, false, false, true"
      })
  void flatSelectors(
      String selector,
      boolean olderNormal,
      boolean newerNormal,
      boolean olderPreview,
      boolean newerPreview) {
    for (boolean preview : new boolean[] {false, true}) {
      when(commonConfig.isV3Preview()).thenReturn(preview);
      InstrumentationModule older = new RatpackInstrumentationModule();
      InstrumentationModule newer = new RatpackHttpClientInstrumentationModule();
      for (boolean enabled : new boolean[] {false, true}) {
        ConfigProperties config = mock(ConfigProperties.class);
        when(config.getBoolean(anyString())).thenReturn(null);
        when(config.getBoolean("otel.instrumentation." + selector + ".enabled"))
            .thenReturn(enabled);
        AgentDistributionConfig distribution = AgentDistributionConfig.fromConfigProperties(config);
        for (boolean defaultEnabled : new boolean[] {false, true}) {
          assertThat(
                  distribution.isInstrumentationEnabled(
                      older.instrumentationNames(), defaultEnabled))
              .isEqualTo((preview ? olderPreview : olderNormal) ? enabled : defaultEnabled);
          assertThat(
                  distribution.isInstrumentationEnabled(
                      newer.instrumentationNames(), defaultEnabled))
              .isEqualTo((preview ? newerPreview : newerNormal) ? enabled : defaultEnabled);
        }
      }
    }
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "ratpack, true, true, true, true",
        "ratpack-1.4, true, false, true, true",
        "ratpack-1.7, false, true, false, false",
        "ratpack-1.4-core, false, false, true, false",
        "ratpack-1.4-http-client, false, false, false, true"
      })
  void declarativeSelectors(
      String selector,
      boolean olderNormal,
      boolean newerNormal,
      boolean olderPreview,
      boolean newerPreview)
      throws Exception {
    YAMLMapper mapper = new YAMLMapper();
    for (boolean preview : new boolean[] {false, true}) {
      when(commonConfig.isV3Preview()).thenReturn(preview);
      InstrumentationModule older = new RatpackInstrumentationModule();
      InstrumentationModule newer = new RatpackHttpClientInstrumentationModule();
      for (boolean enabled : new boolean[] {false, true}) {
        for (boolean defaultEnabled : new boolean[] {false, true}) {
          AgentDistributionConfig distribution =
              mapper.readValue(
                  "instrumentation:\n"
                      + "  default_enabled: "
                      + defaultEnabled
                      + "\n  "
                      + (enabled ? "enabled" : "disabled")
                      + ":\n    - "
                      + selector.replace('-', '_'),
                  AgentDistributionConfig.class);
          assertThat(
                  distribution.isInstrumentationEnabled(
                      older.instrumentationNames(), distribution.isInstrumentationDefaultEnabled()))
              .isEqualTo((preview ? olderPreview : olderNormal) ? enabled : defaultEnabled);
          assertThat(
                  distribution.isInstrumentationEnabled(
                      newer.instrumentationNames(), distribution.isInstrumentationDefaultEnabled()))
              .isEqualTo((preview ? newerPreview : newerNormal) ? enabled : defaultEnabled);
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void ownerTakesPrecedenceOverNarrowerSelectors(boolean preview) {
    when(commonConfig.isV3Preview()).thenReturn(preview);
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.ratpack.enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4-core.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4-http-client.enabled"))
        .thenReturn(true);
    AgentDistributionConfig distribution = AgentDistributionConfig.fromConfigProperties(config);

    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackInstrumentationModule().instrumentationNames(), true))
        .isFalse();
    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackHttpClientInstrumentationModule().instrumentationNames(), true))
        .isFalse();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void declarativeConflicts(boolean preview) throws Exception {
    when(commonConfig.isV3Preview()).thenReturn(preview);
    AgentDistributionConfig distribution =
        new YAMLMapper()
            .readValue(
                "instrumentation:\n"
                    + "  enabled: [ratpack_1.7, ratpack_1.4_core, ratpack_1.4_http_client]\n"
                    + "  disabled: [ratpack_1.4]",
                AgentDistributionConfig.class);
    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackInstrumentationModule().instrumentationNames(), true))
        .isFalse();
    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackHttpClientInstrumentationModule().instrumentationNames(), true))
        .isEqualTo(!preview);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void flatOwnerAndClientSelectorsConflict(boolean preview) {
    when(commonConfig.isV3Preview()).thenReturn(preview);
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4.enabled")).thenReturn(false);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4-http-client.enabled"))
        .thenReturn(true);
    AgentDistributionConfig distribution = AgentDistributionConfig.fromConfigProperties(config);

    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackInstrumentationModule().instrumentationNames(), true))
        .isFalse();
    assertThat(
            distribution.isInstrumentationEnabled(
                new RatpackHttpClientInstrumentationModule().instrumentationNames(), true))
        .isEqualTo(!preview);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void noNewRenameWarnings(boolean preview) {
    when(commonConfig.isV3Preview()).thenReturn(preview);
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean("otel.instrumentation.ratpack-1.7.enabled")).thenReturn(true);
    when(config.getBoolean("otel.instrumentation.ratpack-1.4.enabled")).thenReturn(false);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));

    Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
    Handler handler = mock(Handler.class);
    logger.addHandler(handler);
    try {
      new RatpackHttpClientInstrumentationModule();
    } finally {
      logger.removeHandler(handler);
    }

    verify(handler, never()).publish(any());
  }
}
