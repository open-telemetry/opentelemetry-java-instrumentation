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
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ContextPropagationDebug {
  private static final Logger logger = LoggerFactory.getLogger(ContextPropagationDebug.class);

  // locations where the context was propagated to another thread (tracking multiple steps is
  // helpful in akka where there is so much recursive async spawning of new work)
  private static final ContextKey<ContextPropagationDebug> THREAD_PROPAGATION_LOCATIONS =
      ContextKey.named("thread-propagation-locations");

  private static final boolean THREAD_PROPAGATION_DEBUGGER =
      Config.get()
          .getBoolean(
              "otel.javaagent.experimental.thread-propagation-debugger.enabled",
              Config.get().isAgentDebugEnabled());

  private static final boolean FAIL_ON_CONTEXT_LEAK =
      Config.get().getBoolean("otel.javaagent.testing.fail-on-context-leak", false);

  // context to which debug locations were added
  private final Context sourceContext;
  private final List<Propagation> locations;
  // context after adding debug locations
  private Context wrappedContext;

  private ContextPropagationDebug(Context sourceContext) {
    this.sourceContext = sourceContext;
    this.locations = new CopyOnWriteArrayList<>();
  }

  public static boolean isThreadPropagationDebuggerEnabled() {
    return THREAD_PROPAGATION_DEBUGGER;
  }

  public static Context appendLocations(
      Context context, StackTraceElement[] locations, Object carrier) {
    ContextPropagationDebug propagationDebug = ContextPropagationDebug.getPropagations(context);
    if (propagationDebug == null) {
      propagationDebug = new ContextPropagationDebug(context);
      context = context.with(THREAD_PROPAGATION_LOCATIONS, propagationDebug);
      propagationDebug.wrappedContext = context;
    }
    propagationDebug.locations.add(0, new Propagation(carrier.getClass().getName(), locations));
    return context;
  }

  public static void debugContextLeakIfEnabled() {
    if (!isThreadPropagationDebuggerEnabled()) {
      return;
    }

    Context current = Context.current();
    if (current != Context.root()) {
      logger.error("Unexpected non-root current context found when extracting remote context!");
      Span currentSpan = Span.fromContextOrNull(current);
      if (currentSpan != null) {
        logger.error("It contains this span: {}", currentSpan);
      }

      debugContextPropagation(current);

      if (FAIL_ON_CONTEXT_LEAK) {
        throw new IllegalStateException("Context leak detected");
      }
    }
  }

  public static Context unwrap(Context context) {
    if (context == null || !isThreadPropagationDebuggerEnabled()) {
      return context;
    }

    ContextPropagationDebug propagationDebug = ContextPropagationDebug.getPropagations(context);
    if (propagationDebug == null) {
      return context;
    }

    // unwrap only if debug locations were the last thing that was added to the context
    return propagationDebug.wrappedContext == context ? propagationDebug.sourceContext : context;
  }

  @Nullable
  private static ContextPropagationDebug getPropagations(Context context) {
    return context.get(THREAD_PROPAGATION_LOCATIONS);
  }

  private static void debugContextPropagation(Context context) {
    ContextPropagationDebug propagationDebug = getPropagations(context);
    if (propagationDebug != null) {
      List<Propagation> propagations = propagationDebug.locations;
      StringBuilder sb = new StringBuilder();
      Iterator<Propagation> i = propagations.iterator();
      while (i.hasNext()) {
        Propagation entry = i.next();
        sb.append("\ncarrier of type: ").append(entry.carrierClassName);
        for (StackTraceElement ste : entry.location) {
          sb.append("\n    ");
          sb.append(ste);
        }
        if (i.hasNext()) {
          sb.append("\nwhich was propagated from:");
        }
      }
      logger.error("a context leak was detected. it was propagated from:{}", sb);
    }
  }

  private static class Propagation {
    public final String carrierClassName;
    public final StackTraceElement[] location;

    public Propagation(String carrierClassName, StackTraceElement[] location) {
      this.carrierClassName = carrierClassName;
      this.location = location;
    }
  }
}
