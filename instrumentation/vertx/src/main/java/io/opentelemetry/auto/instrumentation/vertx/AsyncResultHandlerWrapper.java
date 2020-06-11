package io.opentelemetry.auto.instrumentation.vertx;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AsyncResultHandlerWrapper implements Handler<Handler<AsyncResult<?>>> {
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.vertx");

  private final Handler<Handler<AsyncResult<?>>> delegate;
  private final Span parentSpan;

  public AsyncResultHandlerWrapper(final Handler<Handler<AsyncResult<?>>> delegate,
                                   Span parentSpan) {
    this.delegate = delegate;
    this.parentSpan = parentSpan;
  }

  @Override
  public void handle(final Handler<AsyncResult<?>> asyncResultHandler) {
    if (parentSpan != null) {
      try (final Scope scope = TRACER.withSpan(parentSpan)) {
        delegate.handle(asyncResultHandler);
      }
    } else {
      delegate.handle(asyncResultHandler);
    }
  }

  public static Handler<Handler<AsyncResult<?>>> wrapIfNeeded(
      final Handler<Handler<AsyncResult<?>>> delegate,
      final Span parentSpan) {
    if (!(delegate instanceof AsyncResultHandlerWrapper)) {
      log.debug("Wrapping handler {}", delegate);
      return new AsyncResultHandlerWrapper(delegate, parentSpan);
    }
    return delegate;
  }
}
