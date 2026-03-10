/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static java.util.Collections.emptyList;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
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

  public static boolean isInstrumentationEnabled(
      Environment environment, String name, boolean enabledByDefault) {
    if (isDeclarativeConfig(environment)) {
      String snakeCase = name.replace('-', '_');

      List<String> disabled =
          bindList(environment, "otel.distribution.spring_starter.instrumentation.disabled");
      if (disabled.contains(snakeCase)) {
        return false;
      }

      List<String> enabled =
          bindList(environment, "otel.distribution.spring_starter.instrumentation.enabled");
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

  // Environment.getProperty(key, List.class) does not handle indexed properties (e.g.
  // enabled[0]=spring_web); Binder handles both indexed and comma-separated list formats
  private static List<String> bindList(Environment environment, String key) {
    // Binder requires canonical property names (lowercase with hyphens, no underscores)
    String canonicalKey = key.replace('_', '-');
    return Binder.get(environment)
        .bind(canonicalKey, String[].class)
        .map(Arrays::asList)
        .orElse(emptyList());
  }
}
