/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This is a container for exceptions unhandled by servlets and filters, that app-server
 * integrations are interested in (to fail the span exceptionally with attached throwable), but
 * which may be swallowed by an app-server before execution arrives to an app-server integration's
 * OnExit advice.
 */
public class UnhandledServletThrowable {

  public static final ContextKey<AtomicReference<Throwable>> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-unhandled-throwable");

  public static void setThrowableToContext(Throwable t, Context ctx) {
    AtomicReference<Throwable> throwableWanted = ctx.get(UnhandledServletThrowable.CONTEXT_KEY);
    if (throwableWanted != null) {
      throwableWanted.set(t);
    }
  }

  public static Throwable getUnhandledThrowable(Context ctx) {
    AtomicReference<Throwable> unhandledThrowableHolder =
        ctx.get(UnhandledServletThrowable.CONTEXT_KEY);
    if (unhandledThrowableHolder != null) {
      return unhandledThrowableHolder.get();
    }
    return null;
  }
}
