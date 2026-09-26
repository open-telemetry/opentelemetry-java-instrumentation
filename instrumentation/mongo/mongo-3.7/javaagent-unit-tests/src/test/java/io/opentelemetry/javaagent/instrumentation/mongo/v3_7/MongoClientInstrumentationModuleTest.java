/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_7;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.logging.Level.WARNING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.javaagent.extension.instrumentation.internal.DeprecatedInstrumentationNames;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
    assertThat(module.instrumentationNames())
        .containsExactly("mongo", "mongo-3.1", "mongo-3.1-core");
  }

  @Test
  void newApiModuleNames() {
    InstrumentationModule module = new MongoClientInstrumentationModule();

    assertThat(module.instrumentationName()).isEqualTo("mongo");
    if (V3_PREVIEW) {
      assertThat(module.instrumentationNames())
          .containsExactly("mongo", "mongo-3.1", "mongo-3.7-core");
    } else {
      assertThat(module.instrumentationNames())
          .containsExactly("mongo", "mongo-3.1", "mongo-3.7", "mongo-3.7-core");
    }
    assertThat(handler.records).isEmpty();
  }

  @ParameterizedTest
  @CsvSource({"true, false, true", "false, true, false"})
  void replacementPrecedesLegacy(boolean replacement, boolean legacy, boolean expected) {
    setEnabled(replacement, legacy, null);

    InstrumentationModule module = new MongoClientInstrumentationModule();

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(expected);
    if (V3_PREVIEW) {
      assertThat(handler.records).isEmpty();
    } else {
      assertLegacyWarning();
    }
  }

  @Test
  void deprecatedNameFallsBackOutsidePreview() {
    setEnabled(null, false, null);

    InstrumentationModule module = new MongoClientInstrumentationModule();

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isEqualTo(V3_PREVIEW);
    if (V3_PREVIEW) {
      assertThat(handler.records).isEmpty();
    } else {
      assertLegacyWarning();
    }
  }

  @Test
  void coreSelectorRemainsIndependent() {
    setEnabled(null, null, false);

    InstrumentationModule module = new MongoClientInstrumentationModule();
    InstrumentationModule legacyModule =
        new io.opentelemetry.javaagent.instrumentation.mongo.v3_1
            .MongoClientInstrumentationModule();

    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(module.instrumentationNames(), true))
        .isFalse();
    assertThat(
            AgentDistributionConfig.get()
                .isInstrumentationEnabled(legacyModule.instrumentationNames(), true))
        .isTrue();
    assertThat(handler.records).isEmpty();
  }

  private static void setEnabled(Boolean replacement, Boolean legacy, Boolean core) {
    ConfigProperties config = mock(ConfigProperties.class);
    when(config.getBoolean(anyString())).thenReturn(null);
    when(config.getBoolean("otel.instrumentation.mongo-3.1.enabled")).thenReturn(replacement);
    when(config.getBoolean("otel.instrumentation.mongo-3.7.enabled")).thenReturn(legacy);
    when(config.getBoolean("otel.instrumentation.mongo-3.7-core.enabled")).thenReturn(core);
    AgentDistributionConfig.set(AgentDistributionConfig.fromConfigProperties(config));
  }

  private void assertLegacyWarning() {
    assertThat(handler.records)
        .singleElement()
        .satisfies(
            record -> {
              assertThat(record.getLevel()).isEqualTo(WARNING);
              assertThat(record.getMessage())
                  .isEqualTo(
                      "otel.instrumentation.{0}.enabled is deprecated; "
                          + "use otel.instrumentation.{1}.enabled instead.");
              assertThat(record.getParameters()).containsExactly("mongo-3.7", "mongo-3.1");
            });
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
