/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfigurationModel;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import javax.annotation.Nullable;

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
          "otel.distribution.spring_starter.module_configuration.default_config.enabled",
          Boolean.class,
          true);
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

  @Nullable
  private static Boolean isExplicitEnabled(ConfigurableEnvironment environment, String name) {
    if (isDeclarativeConfig(environment)) {
      String snakeCase = toSnakeCase(name);

      OpenTelemetryConfigurationModel model = EmbeddedConfigFile.get(environment).getModel();

      //      otel.distribution.spring_starter.module_configuration.default_config.enabled

      //      distribution:
      //        javaagent:
      //          module_configuration:
      //            default_config:
      //              enabled: true
      //            modules:
      //              - name: dropwizard_metrics
      //                config:
      //                  enabled: false

      // todo
      return null;
    } else {
      return environment.getProperty(
          String.format("otel.instrumentation.%s.enabled", name), Boolean.class);
    }
  }
}
