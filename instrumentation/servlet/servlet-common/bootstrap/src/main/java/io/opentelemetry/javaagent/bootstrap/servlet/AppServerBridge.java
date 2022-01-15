/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.servlet;

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

  private final boolean servletShouldRecordException;
  private boolean captureServletAttributes;
  private Throwable exception;

  private AppServerBridge(Builder builder) {
    servletShouldRecordException = builder.recordException;
    captureServletAttributes = builder.captureServletAttributes;
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
   * Test whether servlet attributes should be captured. This method will return true only on the
   * first call with given context.
   *
   * @param context server context
   * @return true when servlet attributes should be captured
   */
  public static boolean captureServletAttributes(Context context) {
    AppServerBridge appServerBridge = context.get(AppServerBridge.CONTEXT_KEY);
    if (appServerBridge != null) {
      boolean result = appServerBridge.captureServletAttributes;
      appServerBridge.captureServletAttributes = false;
      return result;
    }
    return false;
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

  public static class Builder {
    boolean recordException;
    boolean captureServletAttributes;

    /**
     * Use on servers where exceptions thrown during servlet invocation are not propagated to the
     * method where server span is closed. Recorded exception can be retrieved by calling {@link
     * #getException(Context)}
     *
     * @return this builder.
     */
    public Builder recordException() {
      recordException = true;
      return this;
    }

    /**
     * Use on servers where server instrumentation is not based on servlet instrumentation. Setting
     * this flag lets servlet instrumentation know that it should augment server span with servlet
     * specific attributes.
     *
     * @return this builder.
     */
    public Builder captureServletAttributes() {
      captureServletAttributes = true;
      return this;
    }

    /**
     * Attach AppServerBridge to context.
     *
     * @param context server context
     * @return new context with AppServerBridge attached.
     */
    public Context init(Context context) {
      return context.with(AppServerBridge.CONTEXT_KEY, new AppServerBridge(this));
    }
  }
}
