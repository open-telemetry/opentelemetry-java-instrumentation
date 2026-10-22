/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;

public class JedisRequestContext<T> {
  private static final ThreadLocal<JedisRequestContext<?>> contextThreadLocal = new ThreadLocal<>();

  @Nullable private Operation<?> operation;
  @Nullable private Scope scope;
  @Nullable private Throwable firstServerErrorThrowable;
  @Nullable private JedisRequestContext<?> previousContext;
  private boolean hasRequest;
  private boolean captureServerErrors;

  private JedisRequestContext() {}

  @Nullable
  public static <T> JedisRequestContext<T> attach() {
    JedisRequestContext<?> requestContext = contextThreadLocal.get();
    if (requestContext == null) {
      JedisRequestContext<T> newContext = new JedisRequestContext<>();
      contextThreadLocal.set(newContext);
      return newContext;
    }
    // An independent command inside a batch must not put a handled error in the batch.
    if (requestContext.captureServerErrors) {
      JedisRequestContext<T> nestedContext = new JedisRequestContext<>();
      nestedContext.previousContext = requestContext;
      contextThreadLocal.set(nestedContext);
      return nestedContext;
    }
    // Do not start another context for a command delegated by an active ordinary command.
    return null;
  }

  public static <T> JedisRequestContext<T> startBatch(
      Instrumenter<T, Void> instrumenter, T request) {
    Context parentContext = Context.current();
    Context context = null;
    Scope scope = null;
    if (instrumenter.shouldStart(parentContext, request)) {
      context = instrumenter.start(parentContext, request);
      scope = context.makeCurrent();
    }

    JedisRequestContext<T> requestContext = attachForBatch();
    requestContext.scope = scope;
    if (context != null) {
      requestContext.operation = new Operation<>(instrumenter, context, request);
    }
    return requestContext;
  }

  static <T> JedisRequestContext<T> attachForBatch() {
    JedisRequestContext<T> requestContext = new JedisRequestContext<>();
    requestContext.previousContext = contextThreadLocal.get();
    requestContext.hasRequest = true;
    requestContext.captureServerErrors = true;
    contextThreadLocal.set(requestContext);
    return requestContext;
  }

  @Nullable
  public Throwable detachAndEnd(@Nullable Throwable methodThrowable) {
    if (scope != null) {
      scope.close();
    }
    if (contextThreadLocal.get() == this) {
      if (previousContext == null) {
        contextThreadLocal.remove();
      } else {
        contextThreadLocal.set(previousContext);
      }
    }
    Throwable completionThrowable = methodThrowable;
    if (completionThrowable == null && captureServerErrors) {
      completionThrowable = firstServerErrorThrowable;
    }
    if (operation != null) {
      operation.end(completionThrowable);
    }
    return completionThrowable;
  }

  public static void captureServerError(@Nullable Throwable throwable) {
    if (throwable == null) {
      return;
    }
    JedisRequestContext<?> requestContext = contextThreadLocal.get();
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
    JedisRequestContext<?> requestContext = contextThreadLocal.get();
    if (requestContext == null || requestContext.hasRequest) {
      // End immediately when there is no enclosing operation or a batch is tracking the request.
      endSpan(instrumenter, context, request, throwable);
    } else {
      requestContext.operation = new Operation<>(instrumenter, context, request);
      requestContext.hasRequest = true;
    }
  }

  private static <T> void endSpan(
      Instrumenter<T, Void> instrumenter,
      Context context,
      T request,
      @Nullable Throwable throwable) {
    instrumenter.end(context, request, null, throwable);
  }

  private static final class Operation<T> {
    private final Instrumenter<T, Void> instrumenter;
    private final Context context;
    private final T request;

    private Operation(Instrumenter<T, Void> instrumenter, Context context, T request) {
      this.instrumenter = instrumenter;
      this.context = context;
      this.request = request;
    }

    private void end(@Nullable Throwable throwable) {
      endSpan(instrumenter, context, request, throwable);
    }
  }
}
