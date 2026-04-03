/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class DbConfig {
  private static final Logger logger = Logger.getLogger(DbConfig.class.getName());
  private static final Set<String> warnedDeprecatedProperties = ConcurrentHashMap.newKeySet();

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
    Boolean querySanitizationEnabled =
        getQuerySanitizationEnabled(
            DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName),
            getDeprecatedQuerySanitizationProperty(instrumentationName),
            getQuerySanitizationProperty(instrumentationName));
    if (querySanitizationEnabled != null) {
      return querySanitizationEnabled;
    }
    return isCommonQuerySanitizationEnabled(openTelemetry);
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

    Boolean value = commonConfig.get("db").get("sqlcommenter/development").getBoolean("enabled");
    if (value != null) {
      return value;
    }

    Boolean deprecatedValue =
        commonConfig.get("database").get("sqlcommenter/development").getBoolean("enabled");
    if (deprecatedValue != null) {
      warnIfDeprecatedDeclarativeConfigurationUsed(
          "common.database.sqlcommenter/development.enabled",
          "common.db.sqlcommenter/development.enabled");
      return deprecatedValue;
    }

    return null;
  }

  @Nullable
  private static Boolean getCommonQuerySanitizationEnabled(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    Boolean value =
        getQuerySanitizationEnabled(
            commonConfig.get("db"),
            "otel.instrumentation.common.db-statement-sanitizer.enabled",
            "otel.instrumentation.common.db.query-sanitization.enabled");
    if (value != null) {
      return value;
    }

    Boolean deprecatedValue =
        getQuerySanitizationEnabled(
            commonConfig.get("database"),
            "otel.instrumentation.common.db-statement-sanitizer.enabled",
            "otel.instrumentation.common.db.query-sanitization.enabled");
    if (deprecatedValue != null) {
      warnIfDeprecatedDeclarativeConfigurationUsed(
          "common.database.statement_sanitizer.enabled", "common.db.query_sanitization.enabled");
      return deprecatedValue;
    }

    return null;
  }

  @Nullable
  private static Boolean getQuerySanitizationEnabled(
      DeclarativeConfigProperties config, String deprecatedProperty, String replacementProperty) {
    Boolean value = config.get("query_sanitization").getBoolean("enabled");
    if (value != null) {
      return value;
    }

    Boolean deprecatedValue = config.get("statement_sanitizer").getBoolean("enabled");
    if (deprecatedValue != null) {
      warnIfDeprecatedConfigurationUsed(deprecatedProperty, replacementProperty);
      return deprecatedValue;
    }

    return null;
  }

  private static void warnIfDeprecatedDeclarativeConfigurationUsed(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " declarative configuration is deprecated and will be removed in 3.0. Use "
              + replacementProperty
              + " instead.");
    }
  }

  private static void warnIfDeprecatedConfigurationUsed(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " setting or equivalent declarative configuration is deprecated and will be"
              + " removed in 3.0. Use "
              + replacementProperty
              + " or equivalent declarative configuration instead.");
    }
  }

  private static String getQuerySanitizationProperty(String instrumentationName) {
    return "otel.instrumentation." + instrumentationName + ".query-sanitization.enabled";
  }

  private static String getDeprecatedQuerySanitizationProperty(String instrumentationName) {
    return "otel.instrumentation." + instrumentationName + ".statement-sanitizer.enabled";
  }

  private DbConfig() {}
}
