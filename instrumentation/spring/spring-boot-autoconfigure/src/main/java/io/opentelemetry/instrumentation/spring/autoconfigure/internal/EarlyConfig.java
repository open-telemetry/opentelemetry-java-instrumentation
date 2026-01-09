/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.Collections.emptyList;

import java.util.List;
import javax.annotation.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;
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
            isDeclarativeConfig(environment) ? "otel.disabled" : "otel.sdk.disabled",
            Boolean.class,
            false);
    return !disabled;
  }

  public static boolean isDeclarativeConfig(Environment environment) {
    return environment.getProperty("otel.file_format", String.class) != null;
  }

  public static boolean isDefaultEnabled(Environment environment) {
    if (isDeclarativeConfig(environment)) {
      return environment.getProperty(
          "otel.distribution.spring_starter.instrumentation.default_enabled", Boolean.class, true);
    } else {
      return environment.getProperty(
          "otel.instrumentation.common.default-enabled", Boolean.class, true);
    }
  }

  public static String translatePropertyName(Environment environment, String name) {
    if (isDeclarativeConfig(environment)) {
      if (name.startsWith("otel.instrumentation.")) {
        return toSnakeCase(
            String.format(
                "otel.instrumentation/development.java.%s",
                name.substring("otel.instrumentation.".length())));
      }

      throw new IllegalStateException(
          "No mapping found for property name: " + name + ". Please report this bug.");
    } else {
      return name;
    }
  }

  private static String toSnakeCase(String string) {
    return string.replace('-', '_');
  }

  public static boolean isInstrumentationEnabled(
      ConfigurableEnvironment environment, String name, boolean defaultValue) {
    Boolean explicit = isExplicitEnabled(environment, name);
    if (explicit != null) {
      return explicit;
    }
    return defaultValue && isDefaultEnabled(environment);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  public static Boolean isExplicitEnabled(ConfigurableEnvironment environment, String name) {
    if (isDeclarativeConfig(environment)) {
      String snakeCase = name.replace('-', '_');

      List<String> enabled =
          environment.getProperty(
              "otel.distribution.spring_starter.instrumentation.enabled", List.class, emptyList());
      if (enabled.contains(snakeCase)) {
        return true;
      }

      List<String> disabled =
          environment.getProperty(
              "otel.distribution.spring_starter.instrumentation.disabled", List.class, emptyList());
      if (disabled.contains(snakeCase)) {
        return false;
      }

      return null;
    } else {
      return environment.getProperty(
          String.format("otel.instrumentation.%s.enabled", name), Boolean.class);
    }
  }
}
