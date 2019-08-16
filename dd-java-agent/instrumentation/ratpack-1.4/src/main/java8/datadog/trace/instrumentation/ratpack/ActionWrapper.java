package datadog.trace.instrumentation.ratpack;

import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.util.GlobalTracer;
import lombok.extern.slf4j.Slf4j;
import ratpack.func.Action;

@Slf4j
public class ActionWrapper<T> implements Action<T> {
  private final Action<T> delegate;
  private final Span span;

  private ActionWrapper(final Action<T> delegate, final Span span) {
    assert span != null;
    this.delegate = delegate;
    this.span = span;
  }

  @Override
  public void execute(final T t) throws Exception {
    try (final Scope scope = GlobalTracer.get().scopeManager().activate(span, false)) {
      if (scope instanceof TraceScope) {
        ((TraceScope) scope).setAsyncPropagation(true);
      }
      delegate.execute(t);
    }
  }

  public static <T> Action<T> wrapIfNeeded(final Action<T> delegate, final Span span) {
    if (delegate instanceof ActionWrapper || span == null) {
      return delegate;
    }
    log.debug("Wrapping action task {}", delegate);
    return new ActionWrapper(delegate, span);
  }
}
