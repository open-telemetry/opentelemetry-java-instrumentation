package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.trace.context.ScopeListener;
import io.opentracing.Scope;
import io.opentracing.Span;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextualScopeManager {
  static final ThreadLocal<DDScope> tlsScope = new ThreadLocal<>();
  final List<ScopeListener> scopeListeners = new CopyOnWriteArrayList<>();

  public Scope activate(final Span span, final boolean finishOnClose) {
    if (span instanceof DDSpan) {
      return new ContinuableScope(this, (DDSpan) span, finishOnClose);
    } else {
      return new SimpleScope(this, span, finishOnClose);
    }
  }

  public Scope activate(final Span span) {
    return activate(span, false);
  }

  public Scope active() {
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
