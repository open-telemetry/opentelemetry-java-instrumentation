/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class JarAnalyzerConfigTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void replacementEnabledSettingTakesPrecedence(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties deprecatedConfig = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("enabled")).thenReturn(enabled);
    when(deprecatedConfig.getBoolean("enabled")).thenReturn(!enabled);

    Logger logger = Logger.getLogger(JarAnalyzerConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(JarAnalyzerConfig.getInstrumentationName(config, deprecatedConfig))
          .isEqualTo(enabled ? "io.opentelemetry.runtime-telemetry" : null);
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void deprecatedEnabledSettingIsUsedAsFallback(boolean enabled) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties deprecatedConfig = mock(DeclarativeConfigProperties.class);
    when(config.getBoolean("enabled")).thenReturn(null);
    when(deprecatedConfig.getBoolean("enabled")).thenReturn(enabled);

    Logger logger = Logger.getLogger(JarAnalyzerConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(JarAnalyzerConfig.getInstrumentationName(config, deprecatedConfig))
          .isEqualTo(enabled ? "io.opentelemetry.runtime-telemetry-java8" : null);
      assertThat(handler.records)
          .singleElement()
          .extracting(LogRecord::getMessage)
          .isEqualTo(
              "otel.instrumentation.runtime-telemetry.package-emitter.enabled is deprecated and"
                  + " will be removed in 3.0. Use"
                  + " otel.instrumentation.runtime-telemetry.experimental.package-emitter.enabled"
                  + " instead.");
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @CsvSource({"100, 0", "0, 100"})
  void replacementJarsPerSecondTakesPrecedence(int jarsPerSecond, int deprecatedJarsPerSecond) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties deprecatedConfig = mock(DeclarativeConfigProperties.class);
    when(config.getInt("jars_per_second", -1)).thenReturn(jarsPerSecond);
    when(deprecatedConfig.getInt("jars_per_second", -1)).thenReturn(deprecatedJarsPerSecond);

    Logger logger = Logger.getLogger(JarAnalyzerConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(JarAnalyzerConfig.getJarsPerSecond(config, deprecatedConfig))
          .isEqualTo(jarsPerSecond);
      assertThat(handler.records).isEmpty();
    } finally {
      logger.removeHandler(handler);
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {100, 0})
  void deprecatedJarsPerSecondIsUsedAsFallback(int jarsPerSecond) {
    DeclarativeConfigProperties config = mock(DeclarativeConfigProperties.class);
    DeclarativeConfigProperties deprecatedConfig = mock(DeclarativeConfigProperties.class);
    when(config.getInt("jars_per_second", -1)).thenReturn(-1);
    when(deprecatedConfig.getInt("jars_per_second", -1)).thenReturn(jarsPerSecond);

    Logger logger = Logger.getLogger(JarAnalyzerConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(JarAnalyzerConfig.getJarsPerSecond(config, deprecatedConfig))
          .isEqualTo(jarsPerSecond);
      assertThat(handler.records)
          .singleElement()
          .extracting(LogRecord::getMessage)
          .isEqualTo(
              "otel.instrumentation.runtime-telemetry.package-emitter.jars-per-second is"
                  + " deprecated and will be removed in 3.0. Use"
                  + " otel.instrumentation.runtime-telemetry.experimental.package-emitter.jars-per-second"
                  + " instead.");
    } finally {
      logger.removeHandler(handler);
    }
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
