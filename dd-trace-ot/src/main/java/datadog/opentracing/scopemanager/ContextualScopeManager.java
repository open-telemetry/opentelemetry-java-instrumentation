package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.trace.context.ScopeListener;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

public class ContextualScopeManager implements ScopeManager {
  static final ThreadLocal<Scope> tlsScope = new ThreadLocal<>();
  final Deque<ScopeContext> scopeContexts = new ConcurrentLinkedDeque<>();
  final List<ScopeListener> scopeListeners = new CopyOnWriteArrayList<>();

  @Override
  public Scope activate(final Span span, final boolean finishOnClose) {
    for (final ScopeContext context : scopeContexts) {
      if (context.inContext()) {
        return context.activate(span, finishOnClose);
      }
    }
    if (span instanceof DDSpan) {
      return new ContinuableScope(this, (DDSpan) span, finishOnClose);
    } else {
      return new SimpleScope(this, span, finishOnClose);
    }
  }

  @Override
  public Scope active() {
    for (final ScopeContext csm : scopeContexts) {
      if (csm.inContext()) {
        return csm.active();
      }
    }
    return tlsScope.get();
  }

  @Deprecated
  public void addScopeContext(final ScopeContext context) {
    scopeContexts.addFirst(context);
  }

  /** Attach a listener to scope activation events */
  public void addScopeListener(final ScopeListener listener) {
    scopeListeners.add(listener);
  }
}
