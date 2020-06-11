package io.opentelemetry.auto.instrumentation.vertx;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class AsyncResultConsumerWrapper implements Consumer<Handler<AsyncResult<?>>> {
  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.vertx");

  private final Consumer<Handler<AsyncResult<?>>> delegate;
  private final Span parentSpan;

  public AsyncResultConsumerWrapper(final Consumer<Handler<AsyncResult<?>>> delegate,
      Span parentSpan) {
    this.delegate = delegate;
    this.parentSpan = parentSpan;
  }

  @Override
  public void accept(final Handler<AsyncResult<?>> asyncResultHandler) {
    if (parentSpan != null) {
      try (final Scope scope = TRACER.withSpan(parentSpan)) {
        delegate.accept(asyncResultHandler);
      }
    } else {
      delegate.accept(asyncResultHandler);
    }
  }

  public static Consumer<Handler<AsyncResult<?>>> wrapIfNeeded(
      final Consumer<Handler<AsyncResult<?>>> delegate,
      final Span parentSpan) {
    if (!(delegate instanceof AsyncResultConsumerWrapper)) {
      log.debug("Wrapping consumer {}", delegate);
      return new AsyncResultConsumerWrapper(delegate, parentSpan);
    }
    return delegate;
  }
}
