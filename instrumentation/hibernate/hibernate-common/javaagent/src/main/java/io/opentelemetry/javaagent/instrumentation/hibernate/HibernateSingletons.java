/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hibernate;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public class HibernateSingletons {

  private static final Instrumenter<String, Void> INSTANCE;

  static {
    INSTANCE =
        Instrumenter.<String, Void>builder(
                GlobalOpenTelemetry.get(), "io.opentelemetry.hibernate-common", s -> s)
            .newInstrumenter();
  }

  public static Instrumenter<String, Void> instrumenter() {
    return INSTANCE;
  }
}
