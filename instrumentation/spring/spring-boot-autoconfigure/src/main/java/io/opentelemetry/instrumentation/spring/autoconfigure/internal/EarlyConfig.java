/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.Collections.emptyList;

import java.util.List;
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

  @SuppressWarnings("unchecked") // Spring's Environment.getProperty with List.class returns raw
  public static boolean isInstrumentationEnabled(
      Environment environment, String name, boolean enabledByDefault) {
    if (isDeclarativeConfig(environment)) {
      String snakeCase = name.replace('-', '_');

      List<String> disabled =
          environment.getProperty(
              "otel.distribution.spring_starter.instrumentation.disabled", List.class, emptyList());
      if (disabled.contains(snakeCase)) {
        return false;
      }

      List<String> enabled =
          environment.getProperty(
              "otel.distribution.spring_starter.instrumentation.enabled", List.class, emptyList());
      if (enabled.contains(snakeCase)) {
        return true;
      }

      if (!enabledByDefault) {
        return false;
      }
      return environment.getProperty(
          "otel.distribution.spring_starter.instrumentation.default_enabled", Boolean.class, true);
    }

    Boolean explicit =
        environment.getProperty(
            String.format("otel.instrumentation.%s.enabled", name), Boolean.class);
    if (explicit != null) {
      return explicit;
    }
    if (!enabledByDefault) {
      return false;
    }
    return environment.getProperty(
        "otel.instrumentation.common.default-enabled", Boolean.class, true);
  }
}
