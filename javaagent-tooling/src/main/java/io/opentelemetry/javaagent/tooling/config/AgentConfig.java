/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public final class AgentConfig {

  public static boolean isInstrumentationEnabled(
      ConfigProperties config, Iterable<String> instrumentationNames, boolean defaultEnabled) {
    for (String name : instrumentationNames) {
      String propertyName = "otel.instrumentation." + name + ".enabled";
      Boolean enabled = config.getBoolean(propertyName);
      if (enabled != null) {
        return enabled;
      }
    }
    return defaultEnabled;
  }

  public static boolean isDebugModeEnabled(ConfigProperties config) {
    return config.getBoolean("otel.javaagent.debug", false);
  }

  private AgentConfig() {}
}
