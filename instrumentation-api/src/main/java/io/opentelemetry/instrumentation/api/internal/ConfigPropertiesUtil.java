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
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConfigPropertiesUtil {

  private static final boolean supportsDeclarativeConfig = supportsDeclarativeConfig();

  private static boolean supportsDeclarativeConfig() {
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
   * <p>It's recommended to use {@link #getBoolean(OpenTelemetry, String...)} instead to support
   * Declarative Config.
   */
  public static Optional<Boolean> getBoolean(String propertyName) {
    return Optional.ofNullable(getString(propertyName)).map(Boolean::parseBoolean);
  }

  /**
   * Returns the boolean value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  public static Optional<Boolean> getBoolean(OpenTelemetry openTelemetry, String... propertyName) {
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyName);
    if (node != null) {
      return Optional.ofNullable(node.getBoolean(leaf(propertyName)));
    }
    return getBoolean(toSystemProperty(propertyName));
  }

  /**
   * Returns the int value of the given property name from system properties and environment
   * variables.
   */
  public static Optional<Integer> getInt(String propertyName) {
    return Optional.ofNullable(getString(propertyName))
        .flatMap(
            s -> {
              try {
                return Optional.of(Integer.parseInt(s));
              } catch (NumberFormatException ignored) {
                return Optional.empty();
              }
            });
  }

  /**
   * Returns the string value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link #getString(OpenTelemetry, String...)} instead to support
   * Declarative Config.
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
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyName);
    if (node != null) {
      return node.getString(leaf(propertyName));
    }
    return getString(toSystemProperty(propertyName));
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
    DeclarativeConfigProperties node = getDeclarativeConfigNode(openTelemetry, propertyName);
    if (node != null) {
      return node.getScalarList(leaf(propertyName), String.class, defaultValue);
    }
    String value = getString(toSystemProperty(propertyName));
    if (value == null) {
      return defaultValue;
    }
    return filterBlanksAndNulls(value.split(","));
  }

  /** Returns true if the given OpenTelemetry instance supports Declarative Config. */
  public static boolean isDeclarativeConfig(OpenTelemetry openTelemetry) {
    return supportsDeclarativeConfig && openTelemetry instanceof ExtendedOpenTelemetry;
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

  private static String leaf(String[] propertyName) {
    return propertyName[propertyName.length - 1];
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
