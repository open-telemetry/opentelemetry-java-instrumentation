/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;
import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
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
  public static boolean getBoolean(String propertyName, boolean defaultValue) {
    String strValue = getString(propertyName);
    return strValue == null ? defaultValue : Boolean.parseBoolean(strValue);
  }

  /**
   * Returns the boolean value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  public static Optional<Boolean> getBoolean(OpenTelemetry openTelemetry, String... propertyName) {
    return Optional.ofNullable(
        getValue(openTelemetry, propertyName, DeclarativeConfigProperties::getBoolean));
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
   * <p>It's recommended to use {@link #getString(OpenTelemetry, String...)} instead to support
   * Declarative Config.
   */
  @Nullable
  public static String getString(String propertyName) {
    return ConfigUtil.getString(ConfigUtil.normalizePropertyKey(propertyName));
  }

  /**
   * Returns the string value of the given property name from Declarative Config if available,
   * otherwise falls back to system properties and environment variables.
   */
  public static Optional<String> getString(OpenTelemetry openTelemetry, String... propertyName) {
    return Optional.ofNullable(
        getValue(openTelemetry, propertyName, DeclarativeConfigProperties::getString));
  }

  private static <T> T getValue(
      OpenTelemetry openTelemetry,
      String[] propertyName,
      BiFunction<DeclarativeConfigProperties, String, T> getter) {
    DeclarativeConfigProperties instrumentationConfig =
        getConfigProvider(openTelemetry).getInstrumentationConfig();
    DeclarativeConfigProperties node =
        instrumentationConfig == null
            ? empty()
            : instrumentationConfig.getStructured("java", empty());
    // last part is the leaf property
    for (int i = 0; i < propertyName.length - 1; i++) {
      node = node.getStructured(propertyName[i], empty());
    }
    return getter.apply(node, propertyName[propertyName.length - 1]);
  }

  /**
   * Returns the list of strings value of the given property name from Declarative Config if
   * available, otherwise falls back to system properties and environment variables.
   */
  public static List<String> getList(OpenTelemetry openTelemetry, String... propertyName) {
    return getValue(
        openTelemetry,
        propertyName,
        (declarativeConfigProperties, name) ->
            declarativeConfigProperties.getScalarList(name, String.class, emptyList()));
  }

  /** Returns true if the given OpenTelemetry instance supports Declarative Config. */
  public static boolean isDeclarativeConfig(OpenTelemetry openTelemetry) {
    return supportsDeclarativeConfig && openTelemetry instanceof ExtendedOpenTelemetry;
  }

  public static ConfigProvider getConfigProvider(OpenTelemetry openTelemetry) {
    if (isDeclarativeConfig(openTelemetry)) {
      ExtendedOpenTelemetry extendedOpenTelemetry = (ExtendedOpenTelemetry) openTelemetry;
      return extendedOpenTelemetry.getConfigProvider();
    }
    return new AbstractSystemPropertiesConfigProvider() {
      @Override
      protected AbstractSystemPropertiesDeclarativeConfigProperties getProperties(String name) {
        return new SystemPropertiesDeclarativeConfigProperties(name, null);
      }
    };
  }

  public static String toSystemProperty(String[] nodes) {
    return toSystemProperty(new ArrayList<>(Arrays.asList(nodes)));
  }

  public static String toSystemProperty(List<String> nodes) {
    for (int i = 0; i < nodes.size(); i++) {
      String node = nodes.get(i);
      if (node.endsWith("/development")) {
        String prefix = node.contains("experimental") ? "" : "experimental.";
        nodes.set(i, prefix + node.substring(0, node.length() - 12));
      }
    }
    return "otel.instrumentation." + String.join(".", nodes).replace('_', '-');
  }

  private ConfigPropertiesUtil() {}
}
