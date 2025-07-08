/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
      return Objects.requireNonNull(
          DECLARATIVE_CONFIG_PROPERTY_MAPPING.get(name),
          "No mapping found for property name: " + name + ". Please report this bug.");
    } else {
      return name;
    }
  }
}
