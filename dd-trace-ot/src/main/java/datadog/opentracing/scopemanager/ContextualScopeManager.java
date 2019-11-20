package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.opentracing.Span;
import datadog.trace.context.ScopeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextualScopeManager {
  static final ThreadLocal<DDScope> tlsScope = new ThreadLocal<>();
  final List<ScopeListener> scopeListeners = new CopyOnWriteArrayList<>();

  public DDScope activate(final Span span, final boolean finishOnClose) {
    if (span instanceof DDSpan) {
      return new ContinuableScope(this, (DDSpan) span, finishOnClose);
    } else {
      // NoopSpan
      return new SimpleScope(this, span, finishOnClose);
    }
  }

  public DDScope activate(final Span span) {
    return activate(span, false);
  }

  public DDScope active() {
    return tlsScope.get();
  }

  public Span activeSpan() {
    final DDScope active = tlsScope.get();
    return active == null ? null : active.span();
  }

  /** Attach a listener to scope activation events */
  public void addScopeListener(final ScopeListener listener) {
    scopeListeners.add(listener);
  }
}
