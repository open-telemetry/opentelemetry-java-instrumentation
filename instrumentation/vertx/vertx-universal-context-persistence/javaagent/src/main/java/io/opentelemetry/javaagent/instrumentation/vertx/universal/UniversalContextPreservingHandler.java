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
  //  private static final Logger logger =
  //      Logger.getLogger(UniversalContextPreservingHandler.class.getName());

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
//                (storedContext != null) ? storedContext : Context.current();

//    Span currentSpan = Span.fromContext(capturedContext);
//    System.out.println(
//        "UNIVERSAL-HANDLER-CREATED: Handler "
//            + delegate.getClass().getSimpleName()
//            + ", captured span: "
//            + currentSpan.getSpanContext().getSpanId()
//            + ", traceId: "
//            + currentSpan.getSpanContext().getTraceId()
//            + ", source: "
//            + (storedContext != null ? "VERTX-STORED" : "CURRENT")
//            + ", thread: "
//            + Thread.currentThread().getName());

    //    if (logger.isLoggable(Level.FINE)) {
    //      logger.fine(
    //          String.format(
    //              "UNIVERSAL-WRAP: Handler %s, captured context span: %s, traceId: %s",
    //              delegate.getClass().getSimpleName(),
    //              currentSpan.getSpanContext().getSpanId(),
    //              currentSpan.getSpanContext().getTraceId()));
    //    }
  }

  private static Context getStoredVertxContext() {
    try {
      io.vertx.core.Context vertxContext = Vertx.currentContext();
      if (vertxContext != null) {
        return vertxContext.get("otel.context");
        // String currentKey = vertxContext.get("otel.current.context.key");
        // if (currentKey != null) {
        //     Context storedContext = vertxContext.get(currentKey);
        //     if (storedContext != null) {
        //         return storedContext;
        //     }
        // }
//        System.out.println(
//            "[CONTEXT-RETRIEVAL] Vertx context available but no stored OTel context found");
      } else {
//        System.out.println("[CONTEXT-RETRIEVAL] No Vertx context available");
      }
    } catch (RuntimeException e) {
//      System.out.println(
//          "[CONTEXT-RETRIEVAL-ERROR] Failed to get stored context: " + e.getMessage());
    }
    return null;
  }

  @Override
  public void handle(T result) {
//    Span currentSpan = Span.fromContext(capturedContext);
//    System.out.println(
//        "UNIVERSAL-HANDLER-EXECUTE: Handler "
//            + delegate.getClass().getSimpleName()
//            + ", restoring span: "
//            + currentSpan.getSpanContext().getSpanId()
//            + ", traceId: "
//            + currentSpan.getSpanContext().getTraceId()
//            + ", thread: "
//            + Thread.currentThread().getName());

    //    if (logger.isLoggable(Level.FINE)) {
    //      logger.fine(
    //          String.format(
    //              "UNIVERSAL-EXECUTE: Handler %s, restoring context span: %s, traceId: %s, thread:
    // %s",
    //              delegate.getClass().getSimpleName(),
    //              currentSpan.getSpanContext().getSpanId(),
    //              currentSpan.getSpanContext().getTraceId(),
    //              Thread.currentThread().getName()));
    //    }

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
//      System.out.println("UNIVERSAL-WRAP: Handler is null, returning null");
      return handler;
    }
    if (handler instanceof UniversalContextPreservingHandler) {
//      System.out.println(
//          "UNIVERSAL-WRAP: Handler "
//              + handler.getClass().getSimpleName()
//              + " already wrapped, returning original");
      return handler;
    }
//    System.out.println(
//        "UNIVERSAL-WRAP: Creating wrapper for handler " + handler.getClass().getSimpleName());
    return new UniversalContextPreservingHandler<>(handler);
  }
}
