/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConfigPropertiesUtil {

  private static final boolean isIncubator = isIncubator();

  private static boolean isIncubator() {
    try {
      Class.forName("io.opentelemetry.api.incubator.ExtendedOpenTelemetry");
      return true;
    } catch (ClassNotFoundException e) {
      // The incubator module is not available.
      // This only happens in OpenTelemetry API instrumentation tests, where an older version of
      // OpenTelemetry API is used that does not have ExtendedOpenTelemetry.
      // Having the incubator module without ExtendedOpenTelemetry class should still return false
      // for those tests to avoid a ClassNotFoundException.
      return false;
    }
  }

  /**
   * Returns the boolean value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link #getBoolean(OpenTelemetry, boolean, String...)} instead to
   * support Declarative Config.
   */
  public static boolean getBoolean(String propertyName, boolean defaultValue) {
    String strValue = getString(propertyName);
    return strValue == null ? defaultValue : Boolean.parseBoolean(strValue);
  }

  /**
   * Returns the boolean value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  public static boolean getBoolean(
      OpenTelemetry openTelemetry, boolean defaultValue, String... propertyName) {
    return getDeclarativeConfigOrFallback(
        openTelemetry,
        propertyName,
        (node, key) -> node.getBoolean(key, defaultValue),
        (key) -> getBoolean(key, defaultValue));
  }

  /**
   * Returns the int value of the given property name from system properties and environment
   * variables.
   */
  public static int getInt(String propertyName, int defaultValue) {
    String strValue = getString(propertyName);
    if (strValue == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(strValue);
    } catch (NumberFormatException ignored) {
      return defaultValue;
    }
  }

  /**
   * Returns the string value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link #getString(OpenTelemetry, String, String...)} instead to
   * support Declarative Config.
   */
  @Nullable
  public static String getString(String propertyName) {
    String value = System.getProperty(propertyName);
    if (value != null) {
      return value;
    }
    return System.getenv(toEnvVarName(propertyName));
  }

  /**
   * Returns the string value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  @Nullable
  public static String getString(OpenTelemetry openTelemetry, String... propertyName) {
    return getDeclarativeConfigOrFallback(
        openTelemetry, propertyName, (node, key) -> node.getString(key), (key) -> getString(key));
  }

  /**
   * Returns the string value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  public static String getStringOrFallback(
      OpenTelemetry openTelemetry, String defaultValue, String... propertyName) {
    String value = getString(openTelemetry, propertyName);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  /**
   * Returns the list of strings value of the given property name from Declarative Config if
   * available, otherwise falls back to system properties and environment variables.
   */
  public static List<String> getList(
      OpenTelemetry openTelemetry, List<String> defaultValue, String... propertyName) {
    return getDeclarativeConfigOrFallback(
        openTelemetry,
        propertyName,
        (node, key) -> node.getScalarList(key, String.class, defaultValue),
        (key) -> {
          String value = getString(key);
          if (value == null) {
            return defaultValue;
          }
          return filterBlanksAndNulls(value.split(","));
        });
  }

  /** Returns true if the given OpenTelemetry instance supports Declarative Config. */
  public static boolean isDeclarativeConfig(OpenTelemetry openTelemetry) {
    return isIncubator && openTelemetry instanceof ExtendedOpenTelemetry;
  }

  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private static String toEnvVarName(String propertyName) {
    return propertyName.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
  }

  private static <T> T getDeclarativeConfigOrFallback(
      OpenTelemetry openTelemetry,
      String[] propertyName,
      BiFunction<DeclarativeConfigProperties, String, T> getter,
      Function<String, T> fallback) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyName);
    if (node != null) {
      return getter.apply(node, propertyName[propertyName.length - 1]);
    }
    return fallback.apply(toSystemProperty(propertyName));
  }

  @Nullable
  private static DeclarativeConfigProperties getDeclarativeConfigNode(
      OpenTelemetry openTelemetry, String... propertyName) {
    if (isDeclarativeConfig(openTelemetry)) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      ConfigProvider configProvider = extendedOpenTelemetry.getConfigProvider();
      DeclarativeConfigProperties instrumentationConfig = configProvider.getInstrumentationConfig();
      if (instrumentationConfig == null) {
        return empty();
      }
      DeclarativeConfigProperties node = instrumentationConfig.getStructured("java", empty());
      // last part is the leaf property
      for (int i = 0; i < propertyName.length - 1; i++) {
        node = node.getStructured(propertyName[i], empty());
      }
      return node;
    }
    return null;
  }

  public static String toSystemProperty(String[] propertyName) {
    return "otel.instrumentation." + String.join(".", propertyName).replace('_', '-');
  }

  private ConfigPropertiesUtil() {}
}
