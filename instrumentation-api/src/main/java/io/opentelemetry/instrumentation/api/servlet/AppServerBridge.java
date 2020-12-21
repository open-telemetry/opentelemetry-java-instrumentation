/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Helper container for Context attributes for transferring certain information between servlet
 * integration and app-server server handler integrations.
 */
public class AppServerBridge {

  private static final ContextKey<AppServerBridge> CONTEXT_KEY =
      ContextKey.named("opentelemetry-servlet-app-server-bridge");

  public static Context init(Context ctx) {
    return ctx.with(AppServerBridge.CONTEXT_KEY, new AppServerBridge());
  }

  private final AtomicBoolean servletUpdatedServerSpanName = new AtomicBoolean(false);

  /**
   * Check whether given context contains AppServerBridge.
   *
   * @param ctx server context
   * @return <code>true</code> if AppServerBridge is present in the context. <code>false</code>
   *     otherwise.
   */
  public static boolean isPresent(Context ctx) {
    return ctx.get(AppServerBridge.CONTEXT_KEY) != null;
  }

  /**
   * Returns true, if servlet integration has indicated, that it has updated the name for the server
   * span.
   *
   * @param ctx server context
   * @return <code>true</code>, if the server span name was updated by servlet integration, or
   *     <code>false</code> otherwise.
   */
  public static boolean isServerSpanNameUpdatedFromServlet(Context ctx) {
    AppServerBridge appServerBridge = ctx.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null) {
      return appServerBridge.servletUpdatedServerSpanName.get();
    }
    return false;
  }

  /**
   * Indicate that the servlet integration has updated the name for the server span.
   *
   * @param ctx server context
   */
  public static void setServletUpdatedServerSpanName(Context ctx, boolean value) {
    AppServerBridge appServerBridge = ctx.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null) {
      appServerBridge.servletUpdatedServerSpanName.set(value);
    }
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
