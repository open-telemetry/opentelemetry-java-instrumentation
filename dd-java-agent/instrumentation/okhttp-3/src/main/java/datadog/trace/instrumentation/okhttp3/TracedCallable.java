package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import java.util.concurrent.Callable;

/** @author Pavol Loffay */
public class TracedCallable<V> implements Callable<V> {

  private final Callable<V> delegate;
  private final Span span;
  private final Tracer tracer;

  public TracedCallable(final Callable<V> delegate, final Tracer tracer) {
    this.delegate = delegate;
    this.tracer = tracer;
    this.span = tracer.activeSpan();
  }

  @Override
  public V call() throws Exception {
    final Scope scope = span == null ? null : tracer.scopeManager().activate(span, false);
    try {
      return delegate.call();
    } finally {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
