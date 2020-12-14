/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Helper containers for the Context attributes for transferring certain information between servlet
 * integration and app-server server handler integrations.
 */
public class AppServerBridge {

  public static final ContextKey<AtomicReference<Throwable>> THROWABLE_CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-unhandled-throwable");
  public static final ContextKey<AtomicBoolean> SERVLET_SUGGESTED_BETTER_NAME_KEY =
      ContextKey.named("opentelemetry-servlet-better-name-suggested");

  public static Context init(Context ctx) {
    return ctx.with(AppServerBridge.THROWABLE_CONTEXT_KEY, new AtomicReference<>())
        .with(AppServerBridge.SERVLET_SUGGESTED_BETTER_NAME_KEY, new AtomicBoolean(false));
  }

  /**
   * This is for servlet instrumentation to record exceptions unhandled by servlets and filters,
   * that app-server integrations are interested in (to fail the span exceptionally with attached
   * throwable), but which may be swallowed by an app-server before execution arrives to an
   * app-server integration's OnExit advice.
   *
   * @param t throwable
   * @param ctx Context
   */
  public static void setThrowableToContext(Throwable t, Context ctx) {
    AtomicReference<Throwable> throwableWanted = ctx.get(AppServerBridge.THROWABLE_CONTEXT_KEY);
    if (throwableWanted != null) {
      throwableWanted.set(t);
    }
  }

  public static Throwable getUnhandledThrowable(Context ctx) {
    AtomicReference<Throwable> unhandledThrowableHolder =
        ctx.get(AppServerBridge.THROWABLE_CONTEXT_KEY);
    if (unhandledThrowableHolder != null) {
      return unhandledThrowableHolder.get();
    }
    return null;
  }

  /**
   * Returns true, if servlet integration has indicated, that it has set a better name for the
   * server span.
   *
   * @param ctx server context
   * @return <code>true</code>, if a server span name was set by servlet integration, or <code>false
   *     </code> otherwise.
   */
  public static boolean isBetterNameSuggested(Context ctx) {
    AtomicBoolean betterNameSuggestedHolder =
        ctx.get(AppServerBridge.SERVLET_SUGGESTED_BETTER_NAME_KEY);
    if (betterNameSuggestedHolder != null) {
      return betterNameSuggestedHolder.get();
    }
    return false;
  }

  /**
   * Indicate that the servlet integration has set a better name for the server span.
   *
   * @param ctx server context
   */
  public static void setBetterNameSuggested(Context ctx, boolean value) {
    AtomicBoolean betterNameSuggestedHolder =
        ctx.get(AppServerBridge.SERVLET_SUGGESTED_BETTER_NAME_KEY);
    if (betterNameSuggestedHolder != null) {
      betterNameSuggestedHolder.set(value);
    }
  }
}
