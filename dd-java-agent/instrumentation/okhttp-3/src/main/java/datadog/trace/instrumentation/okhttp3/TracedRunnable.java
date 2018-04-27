package datadog.trace.instrumentation.okhttp3;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;

/** @author Pavol Loffay */
public class TracedRunnable implements Runnable {

  private final Runnable delegate;
  private final Span span;
  private final Tracer tracer;

  public TracedRunnable(final Runnable delegate, final Tracer tracer) {
    this.delegate = delegate;
    this.tracer = tracer;
    this.span = tracer.activeSpan();
  }

  @Override
  public void run() {
    final Scope scope = span == null ? null : tracer.scopeManager().activate(span, false);
    try {
      delegate.run();
    } finally {
      if (scope != null) {
        scope.close();
      }
    }
  }
}
