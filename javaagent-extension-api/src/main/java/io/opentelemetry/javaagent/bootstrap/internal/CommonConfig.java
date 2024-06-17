/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

import io.opentelemetry.instrumentation.api.incubator.internal.config.CoreCommonConfig;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class CommonConfig {
  private CommonConfig() {}

  private static final CoreCommonConfig instance =
      new CoreCommonConfig(InstrumentationConfig.get());

  public static CoreCommonConfig get() {
    return instance;
  }
}
