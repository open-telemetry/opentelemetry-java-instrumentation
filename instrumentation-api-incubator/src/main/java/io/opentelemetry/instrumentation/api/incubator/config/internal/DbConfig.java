/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbConfig {
  private static final Logger logger = Logger.getLogger(DbConfig.class.getName());

  public static boolean isCommonQuerySanitizationEnabled(OpenTelemetry openTelemetry) {
    return isCommonQuerySanitizationEnabled(openTelemetry, true);
  }

  public static boolean isCommonQuerySanitizationEnabled(
      OpenTelemetry openTelemetry, boolean defaultValue) {
    Boolean querySanitizationEnabled =
        getCommonDbValue(openTelemetry, DbConfig::getQuerySanitizationEnabled);
    return querySanitizationEnabled != null ? querySanitizationEnabled : defaultValue;
  }

  public static boolean isQuerySanitizationEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    Boolean querySanitizationEnabled =
        getQuerySanitizationEnabled(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName));
    if (querySanitizationEnabled != null) {
      return querySanitizationEnabled;
    }
    return isCommonQuerySanitizationEnabled(openTelemetry);
  }

  public static boolean isCommonSqlCommenterEnabled(
      OpenTelemetry openTelemetry, boolean defaultValue) {
    Boolean sqlCommenterEnabled = getCommonSqlCommenterEnabled(openTelemetry);
    return sqlCommenterEnabled != null ? sqlCommenterEnabled : defaultValue;
  }

  public static boolean isSqlCommenterEnabled(
      OpenTelemetry openTelemetry, String instrumentationName) {
    return isSqlCommenterEnabled(openTelemetry, instrumentationName, false);
  }

  public static boolean isSqlCommenterEnabled(
      OpenTelemetry openTelemetry, String instrumentationName, boolean defaultValue) {
    Boolean sqlCommenterEnabled =
        getSqlCommenterEnabled(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName));
    if (sqlCommenterEnabled != null) {
      return sqlCommenterEnabled;
    }
    return isCommonSqlCommenterEnabled(openTelemetry, defaultValue);
  }

  @Nullable
  private static Boolean getCommonSqlCommenterEnabled(OpenTelemetry openTelemetry) {
    return getCommonDbValue(openTelemetry, DbConfig::getSqlCommenterEnabled);
  }

  @Nullable
  private static Boolean getCommonDbValue(
      OpenTelemetry openTelemetry, Function<DeclarativeConfigProperties, Boolean> configReader) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    Boolean value = configReader.apply(commonConfig.get("db"));
    if (value != null) {
      return value;
    }

    Boolean deprecatedValue = configReader.apply(commonConfig.get("database"));
    if (deprecatedValue != null) {
      logger.warning(
          "common.database is deprecated in declarative configuration"
              + " and has been replaced by common.db.");
      return deprecatedValue;
    }

    return null;
  }

  @Nullable
  private static Boolean getQuerySanitizationEnabled(DeclarativeConfigProperties config) {
    Boolean value = config.get("query_sanitization").getBoolean("enabled");
    if (value != null) {
      return value;
    }

    Boolean deprecatedValue = config.get("statement_sanitizer").getBoolean("enabled");
    if (deprecatedValue != null) {
      logger.warning(
          "statement_sanitizer is deprecated in declarative configuration"
              + " and has been replaced by query_sanitization.");
      return deprecatedValue;
    }

    return null;
  }

  @Nullable
  private static Boolean getSqlCommenterEnabled(DeclarativeConfigProperties config) {
    return config.get("sqlcommenter/development").getBoolean("enabled");
  }

  private DbConfig() {}
}
