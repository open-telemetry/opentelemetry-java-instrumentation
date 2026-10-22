/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactor.v3_1.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Tracks the application context while a Reactor context boundary delivers downstream signals.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ContextBoundaryState {
  private static final ThreadLocal<Boolean> APPLICATION_CONTEXT_BOUNDARY = new ThreadLocal<>();

  private ContextBoundaryState() {}

  public static Scope enter(Context context) {
    Scope scope = context.makeCurrent();
    Boolean previous = APPLICATION_CONTEXT_BOUNDARY.get();
    APPLICATION_CONTEXT_BOUNDARY.set(true);
    return () -> {
      if (previous == null) {
        APPLICATION_CONTEXT_BOUNDARY.remove();
      } else {
        APPLICATION_CONTEXT_BOUNDARY.set(previous);
      }
      scope.close();
    };
  }

  public static boolean isActive() {
    return APPLICATION_CONTEXT_BOUNDARY.get() != null;
  }
}
