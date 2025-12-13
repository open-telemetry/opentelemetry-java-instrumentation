/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
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
   * <p>It's recommended to use {@link ConfigProviderUtil#getConfigProvider(OpenTelemetry)} instead
   * to support Declarative Config.
   */
  public static boolean getBoolean(String propertyName, boolean defaultValue) {
    String strValue = getString(propertyName);
    return strValue == null ? defaultValue : Boolean.parseBoolean(strValue);
  }

  /**
   * Returns the int value of the given property name from system properties and environment
   * variables.
   *
   * <p>It's recommended to use {@link ConfigProviderUtil#getConfigProvider(OpenTelemetry)} instead
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
   * <p>It's recommended to use {@link ConfigProviderUtil#getConfigProvider(OpenTelemetry)} instead
   * to support Declarative Config.
   */
  @Nullable
  public static String getString(String propertyName) {
    return ConfigUtil.getString(ConfigUtil.normalizePropertyKey(propertyName));
  }

  private ConfigPropertiesUtil() {}
}
