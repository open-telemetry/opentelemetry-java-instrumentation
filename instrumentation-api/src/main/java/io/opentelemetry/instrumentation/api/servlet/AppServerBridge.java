/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;

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
    return init(ctx, true);
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

  private AppServerBridge(boolean shouldRecordException) {
    servletShouldRecordException = shouldRecordException;
  }

  /**
   * Returns true, if servlet integration should record exception thrown during servlet invocation
   * in server span. This method should return <code>false</code> on servers where exceptions thrown
   * during servlet invocation are propagated to the method where server span is closed and can be
   * added to server span there and <code>true</code> otherwise.
   *
   * @param ctx server context
   * @return <code>true</code>, if servlet integration should record exception thrown during servlet
   *     invocation in server span, or <code>false</code> otherwise.
   */
  public static boolean shouldRecordException(Context ctx) {
    AppServerBridge appServerBridge = ctx.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null) {
      return appServerBridge.servletShouldRecordException;
    }
    return true;
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
