/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

/**
 * Helper container for Context attributes for transferring certain information between servlet
 * integration and app-server server handler integrations.
 */
public class AppServerBridge {

  private static final ContextKey<AppServerBridge> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-app-server-bridge");

  /**
   * Attach AppServerBridge to context.
   *
   * @param ctx server context
   * @return new context with AppServerBridge attached.
   */
  public static Context init(Context ctx) {
    return init(ctx, /* shouldRecordException= */ true);
  }

  /**
   * Attach AppServerBridge to context.
   *
   * @param ctx server context
   * @param shouldRecordException whether servlet integration should record exception thrown during
   *     servlet invocation in server span. Use <code>false</code> on servers where exceptions
   *     thrown during servlet invocation are propagated to the method where server span is closed
   *     and can be added to server span there and <code>true</code> otherwise.
   * @return new context with AppServerBridge attached.
   */
  public static Context init(Context ctx, boolean shouldRecordException) {
    return ctx.with(AppServerBridge.CONTEXT_KEY, new AppServerBridge(shouldRecordException));
  }

  private final boolean servletShouldRecordException;
  private Throwable exception;

  private AppServerBridge(boolean shouldRecordException) {
    servletShouldRecordException = shouldRecordException;
  }

  /**
   * Record exception that happened during servlet invocation so that app server instrumentation can
   * add it to server span.
   *
   * @param context server context
   * @param exception exception that happened during servlet invocation
   */
  public static void recordException(Context context, Throwable exception) {
    AppServerBridge appServerBridge = context.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null && appServerBridge.servletShouldRecordException) {
      appServerBridge.exception = exception;
    }
  }

  /**
   * Get exception that happened during servlet invocation.
   *
   * @param context server context
   * @return exception that happened during servlet invocation
   */
  @Nullable
  public static Throwable getException(Context context) {
    AppServerBridge appServerBridge = context.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null) {
      return appServerBridge.exception;
    }
    return null;
  }

  /**
   * Class used as key in CallDepthThreadLocalMap for counting servlet invocation depth in
   * Servlet3Advice and Servlet2Advice. We can not use helper classes like Servlet3Advice and
   * Servlet2Advice for determining call depth of server invocation because they can be injected
   * into multiple class loaders.
   *
   * @return class used as a key in CallDepthThreadLocalMap for counting servlet invocation depth
   */
  public static Class<?> getCallDepthKey() {
    class Key {}

    return Key.class;
  }
}
