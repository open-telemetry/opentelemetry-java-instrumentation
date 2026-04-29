/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

public class RunnableWrapper {

  public static Runnable stopPropagation(Runnable runnable) {
    return () -> {
      try (Scope ignored = Context.root().makeCurrent()) {
        runnable.run();
      }
    };
  }

  private RunnableWrapper() {}
}
