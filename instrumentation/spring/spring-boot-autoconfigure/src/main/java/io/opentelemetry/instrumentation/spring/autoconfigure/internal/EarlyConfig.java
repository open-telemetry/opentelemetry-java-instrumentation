/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import org.springframework.core.env.Environment;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class EarlyConfig {
  private EarlyConfig() {}

  public static boolean otelEnabled(Environment environment) {
    boolean disabled =
        environment.getProperty(
            getPropertyName(environment, "otel.sdk.disabled", "otel.disabled"),
            Boolean.class,
            false);
    return !disabled;
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

  public static String translatePropertyName(Environment environment, String name) {
    if (isDeclarativeConfig(environment)) {
      if (name.startsWith("otel.instrumentation.")) {
        return String.format(
                "otel.instrumentation/development.java.%s",
                name.substring("otel.instrumentation.".length()))
            .replace('-', '_');
      }

      throw new IllegalStateException(
          "No mapping found for property name: " + name + ". Please report this bug.");
    } else {
      return name;
    }
  }

  public static boolean isInstrumentationEnabled(
      Environment environment, String name, boolean defaultValue) {
    String property =
        getPropertyName(
            environment,
            String.format("otel.instrumentation.%s.enabled", name),
            String.format("otel.instrumentation/development.java.%s.enabled", name));
    Boolean explicit = environment.getProperty(property, Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    if (!defaultValue) {
      return false;
    }
    return isDefaultEnabled(environment);
  }

  private static String getPropertyName(
      Environment environment, String propertyBased, String declarative) {
    return isDeclarativeConfig(environment) ? declarative : propertyBased;
  }
}
