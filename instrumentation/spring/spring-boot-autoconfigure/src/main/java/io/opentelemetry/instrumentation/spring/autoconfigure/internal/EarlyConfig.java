/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import java.util.HashMap;
import java.util.Map;
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
    DECLARATIVE_CONFIG_PROPERTY_MAPPING.put(
        "otel.instrumentation.common.default-enabled",
        "otel.instrumentation/development.java.common.default.enabled");
  }

  public static boolean otelEnabled(Environment environment) {
    return !environment.getProperty(
        translatePropertyName(environment, "otel.sdk.disabled"), Boolean.class, false);
  }

  public static Boolean isDeclarativeConfig(Environment environment) {
    return environment.getProperty("otel.file_format", String.class) != null;
  }

  public static String translatePropertyName(Environment environment, String name) {
    if (isDeclarativeConfig(environment)) {
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
    } else {
      return name;
    }
  }

  public static boolean isInstrumentationEnabled(
      Environment environment, String name, boolean defaultValue) {
    Boolean explicit =
        environment.getProperty(
            String.format(
                translatePropertyName(environment, "otel.instrumentation.%s.enabled"), name),
            Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    if (!defaultValue) {
      return false;
    }
    return environment.getProperty(
        translatePropertyName(environment, "otel.instrumentation.common.default-enabled"),
        Boolean.class,
        true);
  }
}
