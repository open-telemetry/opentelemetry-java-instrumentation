/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import java.util.Locale;
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
    String value = getString(propertyName);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
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

  private static String toEnvVarName(String propertyName) {
    return propertyName.toUpperCase(Locale.ROOT).replace('-', '_').replace('.', '_');
  }

  private ConfigPropertiesUtil() {}
}
