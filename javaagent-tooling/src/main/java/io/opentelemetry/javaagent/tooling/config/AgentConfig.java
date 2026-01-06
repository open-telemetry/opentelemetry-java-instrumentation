/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.InstrumentationMode;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public final class AgentConfig {

  public static boolean isDebugModeEnabled(ConfigProperties config) {
    return config.getBoolean("otel.javaagent.debug", false);
  }

  public static InstrumentationMode instrumentationMode() {
    return InstrumentationMode.from(
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
            .getString("instrumentation_mode", "default"));
  }

  private AgentConfig() {}
}
