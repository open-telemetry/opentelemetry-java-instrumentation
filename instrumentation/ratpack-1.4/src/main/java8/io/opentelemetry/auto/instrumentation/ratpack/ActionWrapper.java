package io.opentelemetry.auto.instrumentation.ratpack;

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Action;

@Slf4j
public class ActionWrapper<T> implements Action<T> {
  public static final Tracer TRACER = OpenTelemetry.getTracerFactory().get("io.opentelemetry.auto");

  private final Action<T> delegate;
  private final Span span;

  private ActionWrapper(final Action<T> delegate, final Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute(final T t) throws Exception {
    try (final Scope scope = TRACER.withSpan(span)) {
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(final Action<T> delegate) {
    final Span span = TRACER.getCurrentSpan();
    if (delegate instanceof ActionWrapper || !span.getContext().isValid()) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, span);
  }
}
