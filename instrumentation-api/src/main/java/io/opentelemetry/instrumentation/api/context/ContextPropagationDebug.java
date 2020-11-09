/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.context;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.List;

public final class ContextPropagationDebug {

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  private static final ContextKey<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      ContextKey.named("thread-propagation-locations");
  private static final boolean THREAD_PROPAGATION_DEBUGGER =
      Boolean.getBoolean("otel.threadPropagationDebugger");

  public static boolean isThreadPropagationDebuggerEnabled() {
    return THREAD_PROPAGATION_DEBUGGER;
  }

  public static List<StackTraceElement[]> getLocations(Context context) {
    return context.get(THREAD_PROPAGATION_LOCATIONS);
  }

  public static Context withLocations(List<StackTraceElement[]> locations, Context context) {
    return context.with(THREAD_PROPAGATION_LOCATIONS, locations);
  }

  private ContextPropagationDebug() {}
}
