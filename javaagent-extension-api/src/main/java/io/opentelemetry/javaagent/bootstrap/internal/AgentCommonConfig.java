/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.CommonConfig;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class AgentCommonConfig {
  private AgentCommonConfig() {}

  private static final CommonConfig instance = new CommonConfig(GlobalOpenTelemetry.get());

  public static CommonConfig get() {
    return instance;
  }
}
