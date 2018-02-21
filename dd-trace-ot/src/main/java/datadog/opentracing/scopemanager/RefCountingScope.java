package datadog.opentracing.scopemanager;

import io.opentracing.Scope;
import io.opentracing.Span;
import java.util.concurrent.atomic.AtomicInteger;

public class RefCountingScope implements Scope {
  final ContextualScopeManager scopeManager;
  final AtomicInteger refCount;
  private final Span wrapped;
  private final boolean finishOnClose;
  private final Scope toRestore;

  RefCountingScope(
      final ContextualScopeManager scopeManager,
      final AtomicInteger refCount,
      final Span wrapped,
      final boolean finishOnClose) {
    this.scopeManager = scopeManager;
    this.refCount = refCount;
    this.wrapped = wrapped;
    this.finishOnClose = finishOnClose;
    this.toRestore = scopeManager.tlsScope.get();
    scopeManager.tlsScope.set(this);
  }

  @Override
  public void close() {
    if (scopeManager.tlsScope.get() != this) {
      return;
    }

    if (refCount.decrementAndGet() == 0 && finishOnClose) {
      wrapped.finish();
    }

    scopeManager.tlsScope.set(toRestore);
  }

  @Override
  public Span span() {
    return wrapped;
  }

  public Continuation capture() {
    return new Continuation();
  }

  public class Continuation {
    public Continuation() {
      refCount.incrementAndGet();
    }

    public Scope activate() {
      for (final ScopeContext context : scopeManager.scopeContexts) {
        if (context.inContext()) {
          return context.activate(wrapped, finishOnClose);
        }
      }
      return new RefCountingScope(scopeManager, refCount, wrapped, finishOnClose);
    }
  }
}
