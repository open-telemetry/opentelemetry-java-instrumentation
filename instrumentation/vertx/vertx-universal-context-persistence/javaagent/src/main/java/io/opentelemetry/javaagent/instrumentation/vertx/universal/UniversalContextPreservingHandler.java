/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.universal;

//import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

// import java.util.logging.Level;
// import java.util.logging.Logger;

/**
 * Universal context-preserving handler wrapper for all Vertx operations.
 *
 * <p>This wrapper captures the OpenTelemetry context at the time of handler creation and restores
 * it when the handler is executed on the Vertx event loop.
 *
 * <p>Used across all Vertx components (server and clients) to ensure proper context propagation.
 */
public final class UniversalContextPreservingHandler<T> implements Handler<T> {

  private final Handler<T> delegate;
  private final Context capturedContext;

  public UniversalContextPreservingHandler(Handler<T> delegate) {
    this.delegate = delegate;

    // Try to get stored context from Vertx context first (like SQL does)
    Context storedContext = getStoredVertxContext();
    Context currentOtelContext = Context.current();
    this.capturedContext =
        ((currentOtelContext!=null&&currentOtelContext!=Context.root())||storedContext == null)
            ?currentOtelContext
            : storedContext;
  }

  private static Context getStoredVertxContext() {
    try {
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      if (vertxContext != null) {
        return vertxContext.get("otel.context");
      }
    } catch (RuntimeException e) {
//      commenting to trink compiler to not give a warning
    }
    return null;
  }

  @Override
  public void handle(T result) {

    try (Scope scope = capturedContext.makeCurrent()) {
      if (Vertx.currentContext() != null) {
        Vertx.currentContext().put("otel.context", capturedContext);
      }
      delegate.handle(result); // Execute with restored context
    } finally {
      if (Vertx.currentContext() != null) {
        Vertx.currentContext().remove("otel.context");
      }
    }
  }

  /**
   * Safely wraps a handler, returning the original handler if it's null or already wrapped.
   *
   * @param handler the handler to wrap
   * @param <T> the handler type
   * @return the wrapped handler or original if null/already wrapped
   */
  public static <T> Handler<T> wrap(Handler<T> handler) {
    if (handler == null) {
      return handler;
    }
    if (handler instanceof UniversalContextPreservingHandler) {
      return handler;
    }
    return new UniversalContextPreservingHandler<>(handler);
  }
}
