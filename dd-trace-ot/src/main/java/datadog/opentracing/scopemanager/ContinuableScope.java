package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.trace.context.ScopeListener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContinuableScope implements DDScope {
  /** ScopeManager holding the thread-local to this scope. */
  private final ContextualScopeManager scopeManager;
  /**
   * Span contained by this scope. Async scopes will hold a reference to the parent scope's span.
   */
  private final DDSpan spanUnderScope;
  /** If true, finish the span when openCount hits 0. */
  private final boolean finishOnClose;
  /** Scope to placed in the thread local after close. May be null. */
  private final DDScope toRestore;

  ContinuableScope(
      final ContextualScopeManager scopeManager,
      final DDSpan spanUnderScope,
      final boolean finishOnClose) {
    assert spanUnderScope != null : "span must not be null";
    this.scopeManager = scopeManager;
    this.spanUnderScope = spanUnderScope;
    this.finishOnClose = finishOnClose;
    toRestore = scopeManager.tlsScope.get();
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
    } else {
      log.debug(
          "Tried to close {} scope when {} is on top. Ignoring!",
          this,
          scopeManager.tlsScope.get());
    }
  }

  @Override
  public DDSpan span() {
    return spanUnderScope;
  }

  @Override
  public String toString() {
    return super.toString() + "->" + spanUnderScope;
  }
}
