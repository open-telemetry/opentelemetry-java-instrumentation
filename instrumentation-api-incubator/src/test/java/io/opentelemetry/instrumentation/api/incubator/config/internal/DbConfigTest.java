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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DbConfigTest {

  @ParameterizedTest
  @CsvSource(
      value = {
        "unset, unset, true",
        "unset, false, false",
        "unset, true, true",
        "false, unset, false",
        "false, false, false",
        "false, true, false",
        "true, unset, true",
        "true, false, true",
        "true, true, true"
      },
      nullValues = "unset")
  void querySanitizationUsesInstrumentationSettingThenCommonThenDefault(
      Boolean instrumentationEnabled, Boolean commonEnabled, boolean expected) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(instrumentationConfig.get("query_sanitization").getBoolean("enabled"))
        .thenReturn(instrumentationEnabled);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled"))
        .thenReturn(commonEnabled);

    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc")).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void querySanitizationUsesSuppliedDefaultWhenSettingsAreUnset(boolean defaultValue) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    when(openTelemetry.getInstrumentationConfig("jdbc"))
        .thenReturn(DeclarativeConfigProperties.empty());
    when(openTelemetry.getInstrumentationConfig("common"))
        .thenReturn(DeclarativeConfigProperties.empty());

    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc", defaultValue))
        .isEqualTo(defaultValue);
    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry, defaultValue))
        .isEqualTo(defaultValue);
    assertThat(DbConfig.isSqlCommenterEnabled(openTelemetry, "jdbc", defaultValue))
        .isEqualTo(defaultValue);
  }

  @Test
  void commonQuerySanitizationSettingEnablesSanitization() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled")).thenReturn(true);
    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isTrue();
  }

  @ParameterizedTest
  @CsvSource(
      value = {
        "unset, unset, false",
        "unset, false, false",
        "unset, true, true",
        "false, unset, false",
        "false, false, false",
        "false, true, false",
        "true, unset, true",
        "true, false, true",
        "true, true, true"
      },
      nullValues = "unset")
  void sqlCommenterUsesInstrumentationSettingThenCommonThenDefault(
      Boolean instrumentationEnabled, Boolean commonEnabled, boolean expected) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(instrumentationEnabled);
    when(commonConfig.get("db").get("sqlcommenter/development").getBoolean("enabled"))
        .thenReturn(commonEnabled);
    assertThat(DbConfig.isSqlCommenterEnabled(openTelemetry, "jdbc")).isEqualTo(expected);
  }

  @Test
  void instrumentationQuerySanitizationSettingEnablesSanitization() {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("query_sanitization").getBoolean("enabled")).thenReturn(true);
    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc")).isTrue();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void querySanitizationUsesInstanceCommonSetting(boolean commonEnabled) {
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class);
    DeclarativeConfigProperties commonConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties instrumentationConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getInstrumentationConfig("common")).thenReturn(commonConfig);
    when(openTelemetry.getInstrumentationConfig("jdbc")).thenReturn(instrumentationConfig);
    when(instrumentationConfig.get("query_sanitization").getBoolean("enabled")).thenReturn(null);
    when(commonConfig.get("db").get("query_sanitization").getBoolean("enabled"))
        .thenReturn(commonEnabled);

    assertThat(DbConfig.isQuerySanitizationEnabled(openTelemetry, "jdbc")).isEqualTo(commonEnabled);
    assertThat(DbConfig.isCommonQuerySanitizationEnabled(openTelemetry)).isEqualTo(commonEnabled);
  }
}
