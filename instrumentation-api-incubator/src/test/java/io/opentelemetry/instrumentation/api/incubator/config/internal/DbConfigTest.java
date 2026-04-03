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
}
