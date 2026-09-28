/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1.core.v3_7;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class MongoClientInstrumentationModuleTest {

  private static final boolean V3_PREVIEW =
      Boolean.getBoolean("otel.instrumentation.common.v3-preview");

  private final Logger logger = Logger.getLogger(DeprecatedInstrumentationNames.class.getName());
  private final TestHandler handler = new TestHandler();

  @BeforeAll
  static void setUpConfig() {
    ExtendedOpenTelemetry telemetry = mock(ExtendedOpenTelemetry.class);
    ConfigProvider provider = mock(ConfigProvider.class);
    DeclarativeConfigProperties common = mock(DeclarativeConfigProperties.class);
    when(telemetry.getConfigProvider()).thenReturn(provider);
    when(provider.getGeneralInstrumentationConfig()).thenReturn(empty());
    when(provider.getInstrumentationConfig("common")).thenReturn(common);
    when(common.get(anyString())).thenReturn(empty());
    when(common.getBoolean("v3_preview", false)).thenReturn(V3_PREVIEW);
    GlobalOpenTelemetry.set(telemetry);
  }

  @AfterAll
  static void resetConfig() {
    GlobalOpenTelemetry.resetForTest();
  }

  @BeforeEach
  void captureWarnings() {
    logger.addHandler(handler);
  }

  @AfterEach
  void resetDistributionConfig() {
    logger.removeHandler(handler);
    AgentDistributionConfig.resetForTest();
  }

  @Test
  void legacyModuleNames() {
    InstrumentationModule module =
        new io.opentelemetry.javaagent.instrumentation.mongo.v3_1
            .MongoClientInstrumentationModule();

    assertThat(module.instrumentationName()).isEqualTo("mongo");
    if (V3_PREVIEW) {
      assertThat(module.instrumentationNames())
          .containsExactly("mongo", "mongo-3.1", "mongo-3.1-core");
    } else {
      assertThat(module.instrumentationNames()).containsExactly("mongo", "mongo-3.1");
    }
  }

  @Test
  void newApiModuleNames() {
    InstrumentationModule module = new MongoClientInstrumentationModule();

    assertThat(module.instrumentationName()).isEqualTo("mongo");
    if (V3_PREVIEW) {
      assertThat(module.instrumentationNames())
          .containsExactly("mongo", "mongo-3.1", "mongo-3.1-core-3.7");
    } else {
      assertThat(module.instrumentationNames()).containsExactly("mongo", "mongo-3.7");
    }
    assertThat(handler.records).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("flatCases")
  void flatSelectors(
      boolean defaultEnabled,
      Map<String, Boolean> selectors,
      boolean normal31,
      boolean normal37,
      boolean preview31,
      boolean preview37) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    selectors.forEach(
        (name, enabled) ->
            when(config.getBoolean("otel.instrumentation." + name + ".enabled"))
                .thenReturn(enabled));
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));

    assertModulesEnabled(
        defaultEnabled, V3_PREVIEW ? preview31 : normal31, V3_PREVIEW ? preview37 : normal37);
  }

  private static Stream<Arguments> flatCases() {
    return Stream.of(
        argumentSet("default enabled", true, emptyMap(), true, true, true, true),
        argumentSet("default disabled", false, emptyMap(), false, false, false, false),
        argumentSet(
            "owner disabled", true, singletonMap("mongo", false), false, false, false, false),
        argumentSet(
            "owner wins",
            true,
            selectors("mongo", true, "mongo-3.7", false),
            true,
            true,
            true,
            true),
        argumentSet(
            "3.1 disabled", true, singletonMap("mongo-3.1", false), false, true, false, false),
        argumentSet("3.1 enabled", false, singletonMap("mongo-3.1", true), true, false, true, true),
        argumentSet(
            "3.7 disabled", true, singletonMap("mongo-3.7", false), true, false, true, true),
        argumentSet(
            "3.7 enabled", false, singletonMap("mongo-3.7", true), false, true, false, false),
        argumentSet(
            "3.1 core disabled",
            true,
            singletonMap("mongo-3.1-core", false),
            true,
            true,
            false,
            true),
        argumentSet(
            "3.7 core disabled",
            true,
            singletonMap("mongo-3.1-core-3.7", false),
            true,
            true,
            true,
            false),
        argumentSet(
            "distinct legacy selectors",
            true,
            selectors("mongo-3.1", false, "mongo-3.7", true),
            false,
            true,
            false,
            false),
        argumentSet(
            "conflicting legacy selectors",
            true,
            selectors("mongo-3.1", true, "mongo-3.7", false),
            true,
            false,
            true,
            true),
        argumentSet(
            "preview selector precedence",
            true,
            selectors("mongo-3.1", false, "mongo-3.1-core-3.7", true),
            false,
            true,
            false,
            false));
  }

  private static Map<String, Boolean> selectors(
      String first, boolean firstEnabled, String second, boolean secondEnabled) {
    Map<String, Boolean> selectors = new HashMap<>();
    selectors.put(first, firstEnabled);
    selectors.put(second, secondEnabled);
    return selectors;
  }

  @ParameterizedTest
  @MethodSource("declarativeCases")
  void declarativeSelectors(
      boolean defaultEnabled,
      String enabled,
      String disabled,
      boolean normal31,
      boolean normal37,
      boolean preview31,
      boolean preview37)
      throws Exception {
    String config =
        "{\"instrumentation\":{\"default_enabled\":"
            + defaultEnabled
            + ",\"enabled\":["
            + enabled
            + "],\"disabled\":["
            + disabled
            + "]}}";
    AgentDistributionConfig.set(
        new ObjectMapper().readValue(config, AgentDistributionConfig.class));

    assertModulesEnabled(
        defaultEnabled, V3_PREVIEW ? preview31 : normal31, V3_PREVIEW ? preview37 : normal37);
  }

  private static Stream<Arguments> declarativeCases() {
    return Stream.of(
        argumentSet("default enabled", true, "", "", true, true, true, true),
        argumentSet("default disabled", false, "", "", false, false, false, false),
        argumentSet("3.1 disabled", true, "", "\"mongo_3.1\"", false, true, false, false),
        argumentSet("3.7 disabled", true, "", "\"mongo_3.7\"", true, false, true, true),
        argumentSet(
            "new core disabled", true, "", "\"mongo_3.1_core_3.7\"", true, true, true, false),
        argumentSet("owner enabled", false, "\"mongo\"", "\"mongo_3.7\"", true, true, true, true),
        argumentSet(
            "separate selectors",
            true,
            "\"mongo_3.7\"",
            "\"mongo_3.1\"",
            false,
            true,
            false,
            false),
        argumentSet(
            "same name disabled wins",
            true,
            "\"mongo_3.1\"",
            "\"mongo_3.1\"",
            false,
            true,
            false,
            false));
  }

  private void assertModulesEnabled(
      boolean defaultEnabled, boolean baselineEnabled, boolean core37Enabled) {
    InstrumentationModule baseline =
        new io.opentelemetry.javaagent.instrumentation.mongo.v3_1
            .MongoClientInstrumentationModule();
    InstrumentationModule core37 = new MongoClientInstrumentationModule();

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(baseline.instrumentationNames(), defaultEnabled))
        .isEqualTo(baselineEnabled);
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(core37.instrumentationNames(), defaultEnabled))
        .isEqualTo(core37Enabled);
    assertThat(handler.records).isEmpty();
  }

  private static final class TestHandler extends Handler {
    private final List<LogRecord> records = new ArrayList<>();

    @Override
    public void publish(LogRecord record) {
      records.add(record);
    }

    @Override
    public void flush() {}

    @Override
    public void close() {}
  }
}
