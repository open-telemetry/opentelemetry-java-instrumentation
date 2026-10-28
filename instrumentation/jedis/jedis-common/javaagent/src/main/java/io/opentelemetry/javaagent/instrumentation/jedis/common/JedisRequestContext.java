/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class JedisRequestContext<T> implements JedisServerErrorContext {
  private static final ScopedThreadValue<JedisRequestContext<?>> currentRequestContext =
      new ScopedThreadValue<>();

  @Nullable private Instrumenter<T, Void> instrumenter;
  @Nullable private T request;
  @Nullable private Context context;
  @Nullable private Scope scope;
  @Nullable private List<Throwable> serverErrors;
  @Nullable private Throwable firstServerErrorThrowable;
  @Nullable private JedisRequestContext<?> previousContext;
  @Nullable private Object transactionOwner;
  @Nullable private Object replyOwner;
  @Nullable private List<JedisRequestContext<T>> initializationCommands;
  private boolean captureServerErrors;
  private boolean connectionInitialization;

  private JedisRequestContext() {}

  @Nullable
  public static <T> JedisRequestContext<T> attach() {
    JedisRequestContext<?> requestContext = currentRequestContext.get();
    if (requestContext == null) {
      JedisRequestContext<T> newContext = new JedisRequestContext<>();
      currentRequestContext.set(newContext);
      return newContext;
    }
    // An independent command inside a batch must not put a handled error in the batch.
    if (requestContext.captureServerErrors) {
      JedisRequestContext<T> nestedContext = new JedisRequestContext<>();
      nestedContext.previousContext = currentRequestContext.set(nestedContext);
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
    return startBatch(instrumenter, request, transactionOwner, null);
  }

  public static <T> JedisRequestContext<T> startBatch(
      Instrumenter<T, Void> instrumenter,
      T request,
      @Nullable Object transactionOwner,
      @Nullable Object replyOwner) {
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
    requestContext.replyOwner = replyOwner;
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
    requestContext.previousContext = currentRequestContext.set(requestContext);
    requestContext.captureServerErrors = true;
    return requestContext;
  }

  public static <T> JedisRequestContext<T> attachForBatch(Object transactionOwner) {
    JedisRequestContext<T> requestContext = attachForBatch();
    requestContext.transactionOwner = transactionOwner;
    return requestContext;
  }

  @Nullable
  public static <T> JedisRequestContext<T> attachForConnectionInitialization() {
    if (currentRequestContext.get() == null) {
      return null;
    }
    JedisRequestContext<T> requestContext = new JedisRequestContext<>();
    requestContext.previousContext = currentRequestContext.set(requestContext);
    requestContext.connectionInitialization = true;
    return requestContext;
  }

  @Nullable
  public static <T> JedisRequestContext<T> attachForConnectionInitializationCommand() {
    JedisRequestContext<T> initializationContext = current();
    if (initializationContext == null
        || !initializationContext.connectionInitialization
        || initializationContext.initializationCommands == null
        || initializationContext.initializationCommands.isEmpty()) {
      return null;
    }

    JedisRequestContext<T> commandContext = initializationContext.pollInitializationCommand();
    if (commandContext == null) {
      return null;
    }
    currentRequestContext.set(commandContext);
    return commandContext;
  }

  @Nullable
  public static <T> JedisRequestContext<T> attachForConnectionReply(Object connection) {
    JedisRequestContext<?> requestContext = currentRequestContext.get();
    if (requestContext == null) {
      return null;
    }
    if (requestContext.connectionInitialization) {
      return attachForConnectionInitializationCommand();
    }
    // Keep a batch's own replies in its context only until its reply drain completes.
    if (!requestContext.captureServerErrors || requestContext.replyOwner == connection) {
      return null;
    }

    JedisRequestContext<T> nestedContext = new JedisRequestContext<>();
    nestedContext.previousContext = currentRequestContext.set(nestedContext);
    return nestedContext;
  }

  public static void completePipelineReplies(Object connection) {
    JedisRequestContext<?> requestContext = currentRequestContext.get();
    if (requestContext != null
        && requestContext.captureServerErrors
        && requestContext.transactionOwner == null
        && requestContext.replyOwner == connection) {
      // Pipelines drain all replies with getMany before invoking response builders. Transactions
      // still need to read the EXEC reply after getMany drains their queued-command
      // acknowledgments.
      requestContext.replyOwner = null;
    }
  }

  public static void completeConnectionInitializationCommands(
      @Nullable Object responses, @Nullable Throwable throwable) {
    JedisRequestContext<?> initializationContext = current();
    if (initializationContext == null || !initializationContext.connectionInitialization) {
      return;
    }

    if (throwable != null) {
      initializationContext.completePendingInitializationCommands(throwable);
      return;
    }
    if (!(responses instanceof List<?>)) {
      return;
    }

    for (Object response : (List<?>) responses) {
      JedisRequestContext<?> commandContext = initializationContext.pollInitializationCommand();
      if (commandContext == null) {
        break;
      }
      Throwable responseThrowable = response instanceof Throwable ? (Throwable) response : null;
      if (responseThrowable != null && initializationContext.isServerError(responseThrowable)) {
        commandContext.addServerError(responseThrowable);
      }
      commandContext.detachAndEnd(responseThrowable);
    }
  }

  @Nullable
  public static <T> JedisRequestContext<T> attachForTransaction(Object transaction) {
    JedisRequestContext<?> requestContext = currentRequestContext.get();
    if (requestContext != null && requestContext.transactionOwner == transaction) {
      return null;
    }
    return attachForBatch(transaction);
  }

  @Nullable
  public Throwable detachAndEnd(@Nullable Throwable methodThrowable) {
    return detachAndEnd(methodThrowable, null);
  }

  @Nullable
  public Throwable detachAndEnd(@Nullable Throwable methodThrowable, @Nullable Object result) {
    if (connectionInitialization) {
      completePendingInitializationCommands(methodThrowable);
    }
    if (scope != null) {
      scope.close();
    }
    if (currentRequestContext.get() == this) {
      currentRequestContext.restore(previousContext);
    }
    if (previousContext != null && serverErrors != null) {
      // Deferred responses can throw after detachment. Retain confirmation without failing a parent
      // that may handle or ignore these errors.
      for (Throwable serverError : serverErrors) {
        previousContext.addServerError(serverError);
      }
    }
    Throwable completionThrowable = methodThrowable;
    if (completionThrowable == null && captureServerErrors && result instanceof List<?>) {
      for (Object value : (List<?>) result) {
        if (value instanceof Throwable) {
          completionThrowable = (Throwable) value;
          break;
        }
      }
    }
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
    JedisRequestContext<?> requestContext = currentRequestContext.get();
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
    if (requestContext != null && requestContext.connectionInitialization) {
      requestContext.addInitializationCommand(instrumenter, context, request);
      return;
    }
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

  private void addInitializationCommand(
      Instrumenter<T, Void> instrumenter, Context context, T request) {
    JedisRequestContext<T> commandContext = new JedisRequestContext<>();
    commandContext.previousContext = this;
    commandContext.instrumenter = instrumenter;
    commandContext.context = context;
    commandContext.request = request;
    commandContext.associateServerErrorContext(request);
    if (initializationCommands == null) {
      initializationCommands = new ArrayList<>();
    }
    initializationCommands.add(commandContext);
  }

  @Nullable
  private JedisRequestContext<T> pollInitializationCommand() {
    if (initializationCommands == null || initializationCommands.isEmpty()) {
      return null;
    }
    return initializationCommands.remove(0);
  }

  private void completePendingInitializationCommands(@Nullable Throwable throwable) {
    JedisRequestContext<?> commandContext;
    while ((commandContext = pollInitializationCommand()) != null) {
      commandContext.detachAndEnd(throwable);
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
    return (JedisRequestContext<T>) currentRequestContext.get();
  }

  private static <T> void endSpan(
      Instrumenter<T, Void> instrumenter,
      Context context,
      T request,
      @Nullable Throwable throwable) {
    instrumenter.end(context, request, null, throwable);
  }
}
