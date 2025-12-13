/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import java.util.Optional;

public final class AgentConfig {

  public static boolean isInstrumentationEnabled(
      Iterable<String> instrumentationNames, boolean defaultEnabled) {
    for (String name : instrumentationNames) {
      Optional<Boolean> enabled =
          DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "instrumentation", name, "enabled");
      if (enabled.isPresent()) {
        return enabled.get();
      }
    }
    return defaultEnabled;
  }

  public static boolean isDebugModeEnabled() {
    return DeclarativeConfigUtil.getBoolean(
            GlobalOpenTelemetry.get(), "java", "agent", "debug")
        .orElse(false);
  }

  private AgentConfig() {}
}
