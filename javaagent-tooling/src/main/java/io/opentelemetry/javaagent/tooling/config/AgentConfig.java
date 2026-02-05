/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;

public final class AgentConfig {

  public static String instrumentationMode() {
    String mode =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
            .getString("instrumentation_mode", "default");
    if (!mode.equals("default") && !mode.equals("none")) {
      throw new ConfigurationException("Unknown instrumentation mode: " + mode);
    }
    return mode;
  }

  private AgentConfig() {}
}
