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
    DeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, instrumentationName);
    Boolean querySanitizationEnabled =
        getQuerySanitizationEnabled(
            instrumentationConfig,
            "otel.instrumentation." + instrumentationName + ".statement-sanitizer.enabled",
            "otel.instrumentation." + instrumentationName + ".query-sanitization.enabled");
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

    // this variant was never a regular (non-declarative) configuration property name
    Boolean deprecatedValue =
        commonConfig.get("database").get("sqlcommenter/development").getBoolean("enabled");
    if (deprecatedValue != null) {
      warnIfDeprecatedDeclarativeConfigurationUsed(
          ".instrumentation/development.java.common.database.sqlcommenter/development.enabled",
          ".instrumentation/development.java.common.db.sqlcommenter/development.enabled");
      return deprecatedValue;
    }

    Boolean deprecatedPropertyValue =
        commonConfig.get("db_sqlcommenter/development").getBoolean("enabled");
    if (deprecatedPropertyValue != null) {
      warnIfDeprecatedSystemPropertyUsed(
          "otel.instrumentation.common.experimental.db-sqlcommenter.enabled");
      return deprecatedPropertyValue;
    }

    return null;
  }

  @Nullable
  private static Boolean getCommonQuerySanitizationEnabled(OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common");

    Boolean value = commonConfig.get("db").get("query_sanitization").getBoolean("enabled");
    if (value != null) {
      return value;
    }

    // this variant was never a regular (non-declarative) configuration property name
    Boolean deprecatedStatementSanitizerValue =
        commonConfig.get("database").get("statement_sanitizer").getBoolean("enabled");
    if (deprecatedStatementSanitizerValue != null) {
      warnIfDeprecatedDeclarativeConfigurationUsed(
          ".instrumentation/development.java.common.database.statement_sanitizer.enabled",
          ".instrumentation/development.java.common.db.query_sanitization.enabled");
      return deprecatedStatementSanitizerValue;
    }

    // this variant was never a declarative configuration property name
    Boolean deprecatedStatementSanitizerPropertyValue =
        commonConfig.get("db_statement_sanitizer").getBoolean("enabled");
    if (deprecatedStatementSanitizerPropertyValue != null) {
      warnIfDeprecatedSystemPropertyUsed(
          "otel.instrumentation.common.db-statement-sanitizer.enabled",
          "otel.instrumentation.common.db.query-sanitization.enabled");
      return deprecatedStatementSanitizerPropertyValue;
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
      warnIfDeprecatedSettingOrEquivalentUsed(deprecatedProperty, replacementProperty);
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
              + " declarative configuration is deprecated"
              + " and will be removed in a future version. Use "
              + replacementProperty
              + " instead.");
    }
  }

  private static void warnIfDeprecatedSystemPropertyUsed(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " system property is deprecated and will be removed in 3.0. Use "
              + replacementProperty
              + " instead.");
    }
  }

  private static void warnIfDeprecatedSystemPropertyUsed(String deprecatedProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " system property is deprecated and will be removed in a future version. Use"
              + " equivalent declarative configuration instead.");
    }
  }

  private static void warnIfDeprecatedSettingOrEquivalentUsed(
      String deprecatedProperty, String replacementProperty) {
    if (warnedDeprecatedProperties.add(deprecatedProperty)) {
      logger.warning(
          "The "
              + deprecatedProperty
              + " setting and the equivalent declarative configuration property"
              + " are deprecated and will be removed in 3.0. Use "
              + replacementProperty
              + " or equivalent declarative configuration instead.");
    }
  }

  private DbConfig() {}
}
