package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpanContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import java.util.concurrent.atomic.AtomicInteger;

public class ContinuableScope implements Scope {
  final ContextualScopeManager scopeManager;
  final AtomicInteger refCount;
  private final Span wrapped;
  private final boolean finishOnClose;
  private final Scope toRestore;

  ContinuableScope(
      final ContextualScopeManager scopeManager, final Span wrapped, final boolean finishOnClose) {
    this(scopeManager, new AtomicInteger(1), wrapped, finishOnClose);
  }

  private ContinuableScope(
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

  public Continuation capture(final boolean finishOnClose) {
    return new Continuation(this.finishOnClose && finishOnClose);
  }

  public class Continuation {

    private final boolean finishSpanOnClose;

    private Continuation(final boolean finishOnClose) {
      this.finishSpanOnClose = finishOnClose;
      refCount.incrementAndGet();
      if (wrapped.context() instanceof DDSpanContext) {
        final DDSpanContext context = (DDSpanContext) wrapped.context();
        context.getTrace().registerContinuation(this);
      }
    }

    public Scope activate() {
      for (final ScopeContext context : scopeManager.scopeContexts) {
        if (context.inContext()) {
          return context.activate(wrapped, finishSpanOnClose);
        }
      }
      return new ContinuableScope(scopeManager, refCount, wrapped, finishSpanOnClose);
    }
  }
}
