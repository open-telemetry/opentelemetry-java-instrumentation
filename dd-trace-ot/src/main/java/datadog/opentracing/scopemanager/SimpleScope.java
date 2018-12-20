package datadog.opentracing.scopemanager;

import datadog.trace.context.ScopeListener;
import io.opentracing.Scope;
import io.opentracing.Span;

/** Simple scope implementation which does not propagate across threads. */
public class SimpleScope implements Scope {
  private final ContextualScopeManager scopeManager;
  private final Span spanUnderScope;
  private final boolean finishOnClose;
  private final Scope toRestore;

  public SimpleScope(
      final ContextualScopeManager scopeManager,
      final Span spanUnderScope,
      final boolean finishOnClose) {
    this.scopeManager = scopeManager;
    this.spanUnderScope = spanUnderScope;
    this.finishOnClose = finishOnClose;
    this.toRestore = scopeManager.tlsScope.get();
    scopeManager.tlsScope.set(this);
    for (final ScopeListener listener : scopeManager.scopeListeners) {
      listener.afterScopeActivated();
    }
  }

  @Override
  public void close() {
    if (finishOnClose) {
      spanUnderScope.finish();
    }
    for (final ScopeListener listener : scopeManager.scopeListeners) {
      listener.afterScopeClosed();
    }

    if (scopeManager.tlsScope.get() == this) {
      scopeManager.tlsScope.set(toRestore);
      if (toRestore != null) {
        for (final ScopeListener listener : scopeManager.scopeListeners) {
          listener.afterScopeActivated();
        }
      }
    }
  }

  @Override
  public Span span() {
    return spanUnderScope;
  }
}
