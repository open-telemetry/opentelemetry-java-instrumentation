/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContextPropagationDebug {
  private static final Logger log = LoggerFactory.getLogger(ContextPropagationDebug.class);

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  private static final ContextKey<List<StackTraceElement[]>> THREAD_PROPAGATION_LOCATIONS =
      ContextKey.named("thread-propagation-locations");

  private static final boolean THREAD_PROPAGATION_DEBUGGER =
      Config.get()
          .getBooleanProperty(
              "otel.javaagent.experimental.thread-propagation-debugger.enabled",
              Config.get().isAgentDebugEnabled());

  private static final boolean FAIL_ON_CONTEXT_LEAK =
      Config.get().getBooleanProperty("otel.javaagent.testing.fail-on-context-leak", false);

  public static boolean isThreadPropagationDebuggerEnabled() {
    return THREAD_PROPAGATION_DEBUGGER;
  }

  public static Context appendLocations(Context context, StackTraceElement[] locations) {
    List<StackTraceElement[]> currentLocations = ContextPropagationDebug.getLocations(context);
    if (currentLocations == null) {
      currentLocations = new CopyOnWriteArrayList<>();
      context = context.with(THREAD_PROPAGATION_LOCATIONS, currentLocations);
    }
    currentLocations.add(0, locations);
    return context;
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

  private static List<StackTraceElement[]> getLocations(Context context) {
    return context.get(THREAD_PROPAGATION_LOCATIONS);
  }

  private static void debugContextPropagation(Context context) {
    List<StackTraceElement[]> locations = getLocations(context);
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
