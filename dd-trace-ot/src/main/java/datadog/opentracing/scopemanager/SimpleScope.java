package datadog.opentracing.scopemanager;

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
  }

  @Override
  public void close() {
    if (finishOnClose) {
      spanUnderScope.finish();
    }

    if (scopeManager.tlsScope.get() == this) {
      scopeManager.tlsScope.set(toRestore);
    }
  }

  @Override
  public Span span() {
    return spanUnderScope;
  }
}
