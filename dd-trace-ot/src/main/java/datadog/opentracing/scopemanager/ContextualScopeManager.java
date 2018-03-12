package datadog.opentracing.scopemanager;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ContextualScopeManager implements ScopeManager {
  final ThreadLocal<Scope> tlsScope = new ThreadLocal<>();
  final Deque<ScopeContext> scopeContexts = new ConcurrentLinkedDeque<>();

  @Override
  public Scope activate(final Span span, final boolean finishOnClose) {
    for (final ScopeContext context : scopeContexts) {
      if (context.inContext()) {
        return context.activate(span, finishOnClose);
      }
    }
    return new ContinuableScope(this, span, finishOnClose);
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

  public void addScopeContext(final ScopeContext context) {
    scopeContexts.addFirst(context);
  }
}
