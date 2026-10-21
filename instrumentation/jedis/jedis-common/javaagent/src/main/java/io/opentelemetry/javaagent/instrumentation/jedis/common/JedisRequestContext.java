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

  @Nullable private Instrumenter<T, Void> instrumenter;
  @Nullable private T request;
  @Nullable private Context context;
  @Nullable private Throwable firstServerErrorThrowable;
  private boolean captureServerErrors;

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

  @Nullable
  public static <T> JedisRequestContext<T> attachForBatch(T request) {
    JedisRequestContext<T> requestContext = attach();
    if (requestContext != null) {
      requestContext.request = request;
      requestContext.captureServerErrors = true;
    }
    return requestContext;
  }

  @Nullable
  public Throwable detachAndEnd(@Nullable Throwable methodThrowable) {
    contextThreadLocal.remove();
    Throwable completionThrowable = methodThrowable;
    if (completionThrowable == null && captureServerErrors) {
      completionThrowable = firstServerErrorThrowable;
    }
    if (request != null && instrumenter != null && context != null) {
      endSpan(instrumenter, context, request, completionThrowable);
    }
    return completionThrowable;
  }

  public static void captureServerError(@Nullable Throwable throwable) {
    if (throwable == null) {
      return;
    }
    JedisRequestContext<?> requestContext = current();
    if (requestContext != null
        && requestContext.captureServerErrors
        && requestContext.firstServerErrorThrowable == null) {
      requestContext.firstServerErrorThrowable = throwable;
    }
  }

  /**
   * Schedule ending of instrumented operation when current {@link JedisRequestContext} is closed.
   */
  public static <T> void endIfNotAttached(
      Instrumenter<T, Void> instrumenter,
      Context context,
      T request,
      @Nullable Throwable throwable) {
    JedisRequestContext<T> requestContext = current();
    if (requestContext == null || requestContext.request != null) {
      // end the span immediately if we are already tracking a request
      endSpan(instrumenter, context, request, throwable);
    } else {
      requestContext.instrumenter = instrumenter;
      requestContext.context = context;
      requestContext.request = request;
    }
  }

  @Nullable
  @SuppressWarnings("unchecked") // we lose the generic type in ThreadLocal
  private static <T> JedisRequestContext<T> current() {
    return (JedisRequestContext<T>) contextThreadLocal.get();
  }

  private static <T> void endSpan(
      Instrumenter<T, Void> instrumenter,
      Context context,
      T request,
      @Nullable Throwable throwable) {
    instrumenter.end(context, request, null, throwable);
  }
}
