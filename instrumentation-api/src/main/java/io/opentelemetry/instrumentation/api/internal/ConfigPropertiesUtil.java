/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConfigPropertiesUtil {

  /**
   * Returns the boolean value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
   */
  public static boolean getBoolean(String propertyName, boolean defaultValue) {
    Boolean value = getBoolean(propertyName);
    return value == null ? defaultValue : value;
  }

  /**
   * Returns the boolean value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
   */
  @Nullable
  public static Boolean getBoolean(String propertyName) {
    String strValue = getString(propertyName);
    return strValue == null ? null : Boolean.parseBoolean(strValue);
  }

  /**
   * Returns the int value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
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
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
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
   * Returns the string value of the given property name from system properties and environment
   * variables, or the default value if not found.
   *
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
   */
  public static String getString(String propertyName, String defaultValue) {
    String strValue = getString(propertyName);
    return strValue == null ? defaultValue : strValue;
  }

  /**
   * Returns the list of strings value of the given property name from system properties and
   * environment variables, or the default value if not found. The property value is expected to be
   * a comma-separated list.
   *
   * <p>It's recommended to use {@link io.opentelemetry.api.incubator.config.ConfigProvider} instead
   * to support Declarative Config.
   */
  public static List<String> getList(String propertyName, List<String> defaultValue) {
    String value = getString(propertyName);
    if (value == null) {
      return defaultValue;
    }
    return filterBlanksAndNulls(value.split(","));
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

  private ConfigPropertiesUtil() {}
}
