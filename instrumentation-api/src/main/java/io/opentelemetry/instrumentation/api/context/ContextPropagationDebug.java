/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.context;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContextPropagationDebug {
  private static final Logger log = LoggerFactory.getLogger(ContextPropagationDebug.class);

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  private static final ContextKey<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      ContextKey.named("thread-propagation-locations");

  private static final boolean THREAD_PROPAGATION_DEBUGGER =
      Boolean.getBoolean("otel.javaagent.experimental.thread-propagation-debugger.enabled");
  private static final boolean FAIL_ON_CONTEXT_LEAK =
      Boolean.getBoolean("otel.javaagent.testing.fail-on-context-leak");

  public static boolean isThreadPropagationDebuggerEnabled() {
    return THREAD_PROPAGATION_DEBUGGER;
  }

  public static List<StackTraceElement[]> getLocations(Context context) {
    return context.get(THREAD_PROPAGATION_LOCATIONS);
  }

  public static Context withLocations(List<StackTraceElement[]> locations, Context context) {
    return context.with(THREAD_PROPAGATION_LOCATIONS, locations);
  }

  public static void debugContextLeakIfEnabled() {
    if (!isThreadPropagationDebuggerEnabled()) {
      return;
    }

    Context current = Context.current();
    if (current != Context.root()) {
      log.error("Unexpected non-root current context found when extracting remote context!");
      Span currentSpan = Span.fromContextOrNull(current);
      if (currentSpan != null) {
        log.error("It contains this span: {}", currentSpan);
      }

      debugContextPropagation(current);

      if (FAIL_ON_CONTEXT_LEAK) {
        throw new IllegalStateException("Context leak detected");
      }
    }
  }

  private static void debugContextPropagation(Context context) {
    List<StackTraceElement[]> locations = ContextPropagationDebug.getLocations(context);
    if (locations != null) {
      StringBuilder sb = new StringBuilder();
      Iterator<StackTraceElement[]> i = locations.iterator();
      while (i.hasNext()) {
        for (StackTraceElement ste : i.next()) {
          sb.append("\n");
          sb.append(ste);
        }
        if (i.hasNext()) {
          sb.append("\nwhich was propagated from:");
        }
      }
      log.error("a context leak was detected. it was propagated from:{}", sb);
    }
  }

  private ContextPropagationDebug() {}
}
