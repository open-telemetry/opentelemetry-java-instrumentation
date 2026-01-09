/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

  import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

public final class AgentConfig {

  public static boolean isDebugModeEnabled(ConfigProperties config) {
    return config.getBoolean("otel.javaagent.debug", false);
  }

  public static boolean isDefaultEnabled() {
    // todo
    return true;
//    return InstrumentationMode.from(
//        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
//            .getString("instrumentation_mode", "default"));
  }

  private AgentConfig() {}
}
