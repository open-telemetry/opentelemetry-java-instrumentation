/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.Locale;
import net.bytebuddy.agent.builder.AgentBuilder;

public final class AgentConfig {

  public static boolean isInstrumentationEnabled(
      ConfigProperties config, Iterable<String> instrumentationNames, boolean defaultEnabled) {
    // If default is enabled, we want to enable individually,
    // if default is disabled, we want to disable individually.
    boolean anyEnabled = defaultEnabled;
    for (String name : instrumentationNames) {
      String propertyName = "otel.instrumentation." + name + ".enabled";
      boolean enabled = config.getBoolean(propertyName, defaultEnabled);

      if (defaultEnabled) {
        anyEnabled &= enabled;
      } else {
        anyEnabled |= enabled;
      }
    }
    return anyEnabled;
  }

  public static boolean isDebugModeEnabled(ConfigProperties config) {
    return config.getBoolean("otel.javaagent.debug", false);
  }

  public static AgentBuilder.RedefinitionStrategy redefinitionStrategy(ConfigProperties config) {
    String strategyKey =
        config.getString("otel.redefinition.strategy", "retransformation").toUpperCase(Locale.ROOT);
    try {
      AgentBuilder.RedefinitionStrategy strategy =
          AgentBuilder.RedefinitionStrategy.valueOf(strategyKey);
      if (strategy == AgentBuilder.RedefinitionStrategy.DISABLED) {
        throw new ConfigurationException(
            "disabled strategy is not allowed. Set either retransformation or redefinition");
      }
      return strategy;
    } catch (IllegalArgumentException e) {
      throw new ConfigurationException(
          "Unrecognized value for otel.redefinition.strategy: " + strategyKey, e);
    }
  }

  private AgentConfig() {}
}
