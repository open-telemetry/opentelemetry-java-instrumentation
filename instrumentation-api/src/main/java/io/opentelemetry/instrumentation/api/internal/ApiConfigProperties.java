/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesApiBridge;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ApiConfigProperties {

  static boolean isIncubator = isIncubator();

  @Nullable private final DeclarativeConfigPropertiesApiBridge bridge;

  private static boolean isIncubator() {
    try {
      Class.forName("io.opentelemetry.api.incubator.ExtendedOpenTelemetry");
      return true;
    } catch (ClassNotFoundException e) {
      // incubator module is not available
      return false;
    }
  }

  public ApiConfigProperties(OpenTelemetry openTelemetry) {
    if (isIncubator && openTelemetry instanceof ExtendedOpenTelemetry) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      ConfigProvider configProvider = extendedOpenTelemetry.getConfigProvider();
      this.bridge =
          new DeclarativeConfigPropertiesBridgeBuilder()
              .buildApiBridgeFromInstrumentationConfig(configProvider.getInstrumentationConfig());
    } else {
      this.bridge = null;
    }
  }

  public boolean getBoolean(String propertyName, boolean defaultValue) {
    if (bridge == null) {
      return ConfigPropertiesUtil.getBoolean(propertyName, defaultValue);
    }
    return firstNonNull(bridge.getBoolean(propertyName), defaultValue);
  }

  public int getInt(String propertyName, int defaultValue) {
    if (bridge == null) {
      return ConfigPropertiesUtil.getInt(propertyName, defaultValue);
    }
    return firstNonNull(bridge.getInt(propertyName), defaultValue);
  }

  @Nullable
  public String getString(String propertyName) {
    if (bridge == null) {
      return ConfigPropertiesUtil.getString(propertyName);
    }
    return bridge.getString(propertyName);
  }

  public String getString(String propertyName, String defaultValue) {
    return firstNonNull(getString(propertyName), defaultValue);
  }

  public List<String> getList(String propertyName, List<String> defaultValue) {
    if (bridge == null) {
      String value = getString(propertyName);
      if (value == null) {
        return defaultValue;
      }
      return filterBlanksAndNulls(value.split(","));
    }
    List<String> value = bridge.getList(propertyName);
    if (value == null) {
      return defaultValue;
    }
    return value;
  }

  private static List<String> filterBlanksAndNulls(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private static <T> T firstNonNull(@Nullable T value, T defaultValue) {
    return value == null ? defaultValue : value;
  }
}
