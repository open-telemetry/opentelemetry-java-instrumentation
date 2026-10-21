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
  private static final ThreadLocal<Boolean> processingServerErrorReply = new ThreadLocal<>();

  @Nullable private Instrumenter<T, Void> instrumenter;
  @Nullable private T request;
  @Nullable private Context context;
  @Nullable private Throwable throwable;
  @Nullable private Throwable serverErrorThrowable;
  @Nullable private String serverErrorReply;
  private boolean captureServerErrors;
  private boolean serverErrorReplyCaptured;

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
    if (completionThrowable == null) {
      completionThrowable = captureServerErrors ? serverErrorThrowable : throwable;
    }
    if (serverErrorReplyCaptured
        && serverErrorReply != null
        && serverErrorThrowable != null
        && (captureServerErrors || methodThrowable == serverErrorThrowable)
        && request instanceof JedisServerErrorRequest) {
      ((JedisServerErrorRequest) request).setServerErrorReply(serverErrorReply);
    }
    if (request != null && instrumenter != null && context != null) {
      endSpan(instrumenter, context, request, completionThrowable);
    }
    return completionThrowable;
  }

  public static boolean beginServerErrorReply() {
    boolean previous = Boolean.TRUE.equals(processingServerErrorReply.get());
    processingServerErrorReply.set(true);
    return previous;
  }

  public static void captureServerErrorReply(String reply) {
    if (!Boolean.TRUE.equals(processingServerErrorReply.get())) {
      return;
    }
    JedisRequestContext<?> requestContext = current();
    if (requestContext != null && !requestContext.serverErrorReplyCaptured) {
      requestContext.serverErrorReply = reply;
      requestContext.serverErrorReplyCaptured = true;
    }
  }

  public static void endServerErrorReply(boolean previous, @Nullable Throwable throwable) {
    if (previous) {
      processingServerErrorReply.set(true);
    } else {
      processingServerErrorReply.remove();
    }
    JedisRequestContext<?> requestContext = current();
    if (requestContext != null
        && requestContext.serverErrorReplyCaptured
        && requestContext.serverErrorThrowable == null) {
      requestContext.serverErrorThrowable = throwable;
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
      requestContext.throwable = throwable;
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
