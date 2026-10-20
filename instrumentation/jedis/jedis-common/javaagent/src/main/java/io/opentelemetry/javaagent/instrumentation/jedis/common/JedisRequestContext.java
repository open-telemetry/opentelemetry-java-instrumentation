/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

public class JedisRequestContext<T> {
  private static final ThreadLocal<JedisRequestContext<?>> contextThreadLocal = new ThreadLocal<>();

  @Nullable private Instrumenter<T, String> instrumenter;
  @Nullable private T request;
  @Nullable private Context context;
  @Nullable private Throwable throwable;
  @Nullable private String errorType;

  private JedisRequestContext() {}

  @Nullable
  public static <T> JedisRequestContext<T> attach() {
    JedisRequestContext<T> requestContext = current();
    // if there already is an active request context don't start a new one
    if (requestContext != null) {
      return null;
    }
    requestContext = new JedisRequestContext<>();
    contextThreadLocal.set(requestContext);
    return requestContext;
  }

  public void detachAndEnd(@Nullable Throwable terminalThrowable) {
    contextThreadLocal.remove();
    if (request != null && instrumenter != null && context != null) {
      Throwable error = terminalThrowable != null ? terminalThrowable : throwable;
      String response =
          terminalThrowable != null
              ? JedisRedisErrorType.getErrorType(terminalThrowable)
              : errorType;
      endSpan(instrumenter, context, request, response, error);
    }
  }

  /**
   * Schedule ending of instrumented operation when current {@link JedisRequestContext} is closed.
   */
  public static <T> void endIfNotAttached(
      Instrumenter<T, String> instrumenter,
      Context context,
      T request,
      @Nullable String errorType,
      @Nullable Throwable throwable) {
    JedisRequestContext<T> requestContext = current();
    if (requestContext == null || requestContext.request != null) {
      // end the span immediately if we are already tracking a request
      endSpan(instrumenter, context, request, errorType, throwable);
    } else {
      requestContext.instrumenter = instrumenter;
      requestContext.context = context;
      requestContext.request = request;
      requestContext.errorType = errorType;
      requestContext.throwable = throwable;
    }
  }

  @Nullable
  @SuppressWarnings("unchecked") // we lose the generic type in ThreadLocal
  private static <T> JedisRequestContext<T> current() {
    return (JedisRequestContext<T>) contextThreadLocal.get();
  }

  private static <T> void endSpan(
      Instrumenter<T, String> instrumenter,
      Context context,
      T request,
      @Nullable String errorType,
      @Nullable Throwable throwable) {
    instrumenter.end(context, request, errorType, throwable);
  }
}
