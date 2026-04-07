/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class DbConfigTest {

  @Test
  void newCommonQuerySanitizationTakesPrecedenceOverDeprecatedConfig() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled")).thenReturn(true);
    when(commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled"))
        .thenReturn(false);

    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isTrue();
  }

  @Test
  void deprecatedCommonStatementSanitizerConfigIsUsedAsFallback() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled"))
        .thenReturn(false);

    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isFalse();
  }

  @Test
  void deprecatedCommonStatementSanitizerPropertyIsUsedAsFallback() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("db_statement_sanitizer").getBoolean("enabled")).thenReturn(false);

    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isFalse();
  }

  @Test
  void commonSqlCommenterPropertyIsUsedAsFallback() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("db").get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("database").get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("db_sqlcommenter/development").getBoolean("enabled")).thenReturn(true);

    assertThat(DbConfig.isSqlCommenterEnabled(openTelemetry, "jdbc")).isTrue();
  }

  @Test
  void deprecatedCommonSqlCommenterConfigWarningUsesFutureVersionMessage() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("db").get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(null);
    when(commonConfig.get("database").get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(true);

    clearDeprecatedWarnings();
    Logger logger = Logger.getLogger(DbConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(DbConfig.isSqlCommenterEnabled(openTelemetry, "jdbc")).isTrue();

      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The 'instrumentation/development: java: common: database:"
                  + " sqlcommenter/development: enabled' declarative configuration is"
                  + " deprecated and will be removed in a future version. Use"
                  + " 'instrumentation/development: java: common: db:"
                  + " sqlcommenter/development: enabled' instead.");
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  @Test
  void newInstrumentationQuerySanitizationTakesPrecedenceOverDeprecatedConfig() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("query_sanitization").getBoolean("enabled")).thenReturn(true);
    when(instrumentationConfig.get("statement_sanitizer").getBoolean("enabled")).thenReturn(false);

    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc")).isTrue();
  }

  @Test
  void deprecatedInstrumentationQuerySanitizationConfigIsUsedAsFallback() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("statement_sanitizer").getBoolean("enabled")).thenReturn(false);

    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc")).isFalse();
  }

  @Test
  void deprecatedCommonStatementSanitizerConfigWarningUsesFutureVersionMessage() throws Exception {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled")).thenReturn(null);
    when(commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled"))
        .thenReturn(false);

    clearDeprecatedWarnings();
    Logger logger = Logger.getLogger(DbConfig.class.getName());
    TestHandler handler = new TestHandler();
    logger.addHandler(handler);
    try {
      assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isFalse();

      assertThat(handler.records).hasSize(1);
      assertThat(handler.records.get(0).getMessage())
          .isEqualTo(
              "The 'instrumentation/development: java: common: database:"
                  + " statement_sanitizer: enabled' declarative configuration is"
                  + " deprecated and will be removed in a future version. Use"
                  + " 'instrumentation/development: java: common: db: query_sanitization:"
                  + " enabled' instead.");
    } finally {
      logger.removeHandler(handler);
      clearDeprecatedWarnings();
    }
  }

  private static void clearDeprecatedWarnings() throws Exception {
    Field warnedDeprecatedPropertiesField =
        DbConfig.class.getDeclaredField("warnedDeprecatedProperties");
    warnedDeprecatedPropertiesField.setAccessible(true);
    ((Set<?>) warnedDeprecatedPropertiesField.get(null)).clear();
  }

  private static class TestHandler extends Handler {
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
