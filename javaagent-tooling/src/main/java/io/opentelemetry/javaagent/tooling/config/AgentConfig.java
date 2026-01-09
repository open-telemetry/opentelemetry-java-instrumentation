/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

public final class AgentConfig {

  public static boolean isDefaultEnabled() {
    // todo
    return true;
//    return InstrumentationMode.from(
//        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "agent")
//            .getString("instrumentation_mode", "default"));
  }

  private AgentConfig() {}
}
