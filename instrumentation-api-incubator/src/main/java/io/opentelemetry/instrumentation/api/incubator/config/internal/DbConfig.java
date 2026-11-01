/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbConfig {
  public static boolean isCommonQuerySanitizationEnabled(OpenTelemetry openTelemetry) {
    return isCommonQuerySanitizationEnabled(openTelemetry, true);
  }

  public static boolean isCommonQuerySanitizationEnabled(
      OpenTelemetry openTelemetry, boolean defaultValue) {
    Boolean querySanitizationEnabled = getCommonQuerySanitizationEnabled(openTelemetry);
    return querySanitizationEnabled != null ? querySanitizationEnabled : defaultValue;
  }

  public static boolean isQuerySanitizationEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return isQuerySanitizationEnabled(openTelemetry, instrumentationName, true);
  }

  public static boolean isQuerySanitizationEnabled(
      OpenTelemetry openTelemetry, String instrumentationName, boolean defaultValue) {
    DeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName);
    Boolean querySanitizationEnabled =
        instrumentationConfig.get("query_sanitization").getBoolean("enabled");
    if (querySanitizationEnabled != null) {
      return querySanitizationEnabled;
    }
    return isCommonQuerySanitizationEnabled(openTelemetry, defaultValue);
  }

  public static boolean isSqlCommenterEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return isSqlCommenterEnabled(openTelemetry, instrumentationName, false);
  }

  public static boolean isSqlCommenterEnabled(
      OpenTelemetry openTelemetry, String instrumentationName, boolean defaultValue) {
    Boolean sqlCommenterEnabled =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName)
            .get("sqlcommenter/development")
            .getBoolean("enabled");
    if (sqlCommenterEnabled != null) {
      return sqlCommenterEnabled;
    }

    sqlCommenterEnabled = getCommonSqlCommenterEnabled(openTelemetry);
    return sqlCommenterEnabled != null ? sqlCommenterEnabled : defaultValue;
  }

  @Nullable
  private static Boolean getCommonSqlCommenterEnabled(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    return commonConfig.get("db").get("sqlcommenter/development").getBoolean("enabled");
  }

  @Nullable
  private static Boolean getCommonQuerySanitizationEnabled(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    return commonConfig.get("db").get("query_sanitization").getBoolean("enabled");
  }

  private DbConfig() {}
}
