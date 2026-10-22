/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class JedisRequestContext<T> implements JedisServerErrorContext {
  private static final ThreadLocal<JedisRequestContext<?>> contextThreadLocal = new ThreadLocal<>();

  @Nullable private Instrumenter<T, Void> instrumenter;
  @Nullable private T request;
  @Nullable private Context context;
  @Nullable private Scope scope;
  @Nullable private List<Throwable> serverErrors;
  @Nullable private Throwable firstServerErrorThrowable;
  @Nullable private JedisRequestContext<?> previousContext;
  @Nullable private Object transactionOwner;
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
    return startBatch(instrumenter, request, null);
  }

  public static <T> JedisRequestContext<T> startBatch(
      Instrumenter<T, Void> instrumenter, T request, @Nullable Object transactionOwner) {
    Context parentContext = Context.current();
    Context context = null;
    Scope scope = null;
    if (instrumenter.shouldStart(parentContext, request)) {
      context = instrumenter.start(parentContext, request);
      scope = context.makeCurrent();
    }

    JedisRequestContext<T> requestContext =
        transactionOwner == null ? attachForBatch() : attachForBatch(transactionOwner);
    requestContext.request = request;
    requestContext.associateServerErrorContext(request);
    requestContext.scope = scope;
    if (context != null) {
      requestContext.instrumenter = instrumenter;
      requestContext.context = context;
    }
    return requestContext;
  }

  public static <T> JedisRequestContext<T> attachForBatch() {
    JedisRequestContext<T> requestContext = new JedisRequestContext<>();
    requestContext.previousContext = contextThreadLocal.get();
    requestContext.captureServerErrors = true;
    contextThreadLocal.set(requestContext);
    return requestContext;
  }

  public static <T> JedisRequestContext<T> attachForBatch(Object transactionOwner) {
    JedisRequestContext<T> requestContext = attachForBatch();
    requestContext.transactionOwner = transactionOwner;
    return requestContext;
  }

  @Nullable
  public static <T> JedisRequestContext<T> attachForTransaction(Object transaction) {
    JedisRequestContext<?> requestContext = contextThreadLocal.get();
    if (requestContext != null && requestContext.transactionOwner == transaction) {
      return null;
    }
    return attachForBatch(transaction);
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
    if (instrumenter != null && context != null && request != null) {
      endSpan(instrumenter, context, request, completionThrowable);
    }
    return completionThrowable;
  }

  public static void captureServerError(@Nullable Throwable throwable) {
    if (throwable == null) {
      return;
    }
    JedisRequestContext<?> requestContext = contextThreadLocal.get();
    if (requestContext != null) {
      requestContext.addServerError(throwable);
      if (requestContext.captureServerErrors && requestContext.firstServerErrorThrowable == null) {
        requestContext.firstServerErrorThrowable = throwable;
      }
    }
  }

  @Override
  public boolean isServerError(Throwable throwable) {
    List<Throwable> serverErrors = this.serverErrors;
    if (serverErrors != null) {
      for (Throwable serverError : serverErrors) {
        if (serverError == throwable) {
          return true;
        }
      }
    }
    return false;
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
    if (requestContext != null) {
      requestContext.associateServerErrorContext(request);
    }
    if (requestContext == null || requestContext.request != null) {
      // End immediately when there is no enclosing operation or a batch is tracking the request.
      endSpan(instrumenter, context, request, throwable);
    } else {
      requestContext.instrumenter = instrumenter;
      requestContext.context = context;
      requestContext.request = request;
    }
  }

  private void addServerError(Throwable throwable) {
    List<Throwable> serverErrors = this.serverErrors;
    if (serverErrors == null) {
      serverErrors = new ArrayList<>();
      this.serverErrors = serverErrors;
    }
    serverErrors.add(throwable);
  }

  private void associateServerErrorContext(Object request) {
    if (request instanceof JedisServerErrorRequest) {
      ((JedisServerErrorRequest) request).setServerErrorContext(this);
    }
  }

  @Nullable
  @SuppressWarnings("unchecked")
  private static <T> JedisRequestContext<T> current() {
    // A context's instrumenter, request, and OpenTelemetry context are set together by the same
    // generic operation and remain paired until that context is completed.
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
