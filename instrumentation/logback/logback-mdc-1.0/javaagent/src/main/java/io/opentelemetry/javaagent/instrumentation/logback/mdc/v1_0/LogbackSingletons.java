/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class LogbackSingletons {
  private static final boolean ADD_BAGGAGE =
      InstrumentationConfig.get().getBoolean("otel.instrumentation.logback-mdc.add-baggage", false);

  public static boolean addBaggage() {
    return ADD_BAGGAGE;
  }

  private LogbackSingletons() {}
}
