/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EarlyConfig {
  private EarlyConfig() {}

  private static final Map<String, String> DECLARATIVE_CONFIG_PROPERTY_MAPPING = new HashMap<>();

  static {
    DECLARATIVE_CONFIG_PROPERTY_MAPPING.put("otel.sdk.disabled", "otel.disabled");
    DECLARATIVE_CONFIG_PROPERTY_MAPPING.put(
        "otel.instrumentation.%s.enabled", "otel.instrumentation/development.java.%s.enabled");
  }

  public static boolean otelEnabled(Environment environment) {
    return !environment.getProperty(
        translatePropertyName(environment, "otel.sdk.disabled", null), Boolean.class, false);
  }

  public static boolean isDeclarativeConfig(Environment environment) {
    return environment.getProperty("otel.file_format", String.class) != null;
  }

  public static boolean isDefaultEnabled(Environment environment) {
    if (isDeclarativeConfig(environment)) {
      String mode =
          environment.getProperty(
              "otel.instrumentation/development.java.spring_starter.instrumentation_mode",
              String.class,
              "default");

      switch (mode) {
        case "none":
          return false;
        case "default":
          return true;
        default:
          throw new ConfigurationException("Unknown instrumentation mode: " + mode);
      }
    } else {
      return environment.getProperty(
          "otel.instrumentation.common.default-enabled", Boolean.class, true);
    }
  }

  public static String translatePropertyName(
      Environment environment, String name, @Nullable String arg) {
    if (isDeclarativeConfig(environment)) {
      String key = declarativeConfigKey(name);
      if (arg != null) {
        key = String.format(key, arg);
      }
      return key.replace('-', '_');
    } else {
      String key = name;
      if (arg != null) {
        key = String.format(key, arg);
      }
      return key;
    }
  }

  private static String declarativeConfigKey(String name) {
    String value = DECLARATIVE_CONFIG_PROPERTY_MAPPING.get(name);
    if (value != null) {
      return value;
    }
    if (name.startsWith("otel.instrumentation.")) {
      return String.format(
          "otel.instrumentation/development.java.%s",
          name.substring("otel.instrumentation.".length()));
    }

    throw new IllegalStateException(
        "No mapping found for property name: " + name + ". Please report this bug.");
  }

  public static boolean isInstrumentationEnabled(
      Environment environment, String name, boolean defaultValue) {
    Boolean explicit =
        environment.getProperty(
            translatePropertyName(environment, "otel.instrumentation.%s.enabled", name),
            Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    if (!defaultValue) {
      return false;
    }
    return isDefaultEnabled(environment);
  }
}
