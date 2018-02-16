package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

public class ContextualScopeManager implements ScopeManager {
  final ThreadLocal<Scope> tlsScope = new ThreadLocal<>();
  final Set<ScopeContext> scopeContexts = new CopyOnWriteArraySet<>();

  @Override
  public Scope activate(final Span span, final boolean finishOnClose) {
    for (final ScopeContext context : scopeContexts) {
      if (context.inContext()) {
        return context.activate(span, finishOnClose);
      }
    }
    if (span instanceof DDSpan && ((DDSpan) span).context().useRefCounting) {
      return new RefCountingScope(this, new AtomicInteger(1), span);
    } else {
      return new ThreadLocalScope(this, span, finishOnClose);
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

  public void addScopeContext(final ScopeContext context) {
    scopeContexts.add(context);
  }

  class ThreadLocalScope implements Scope {
    private final ContextualScopeManager scopeManager;
    private final Span wrapped;
    private final boolean finishOnClose;
    private final Scope toRestore;

    ThreadLocalScope(
        final ContextualScopeManager scopeManager,
        final Span wrapped,
        final boolean finishOnClose) {
      this.scopeManager = scopeManager;
      this.wrapped = wrapped;
      this.finishOnClose = finishOnClose;
      this.toRestore = scopeManager.tlsScope.get();
      scopeManager.tlsScope.set(this);
    }

    @Override
    public void close() {
      if (scopeManager.tlsScope.get() != this) {
        // This shouldn't happen if users call methods in the expected order. Bail out.
        return;
      }

      if (finishOnClose) {
        wrapped.finish();
      }

      scopeManager.tlsScope.set(toRestore);
    }

    @Override
    public Span span() {
      return wrapped;
    }
  }

  public class RefCountingScope implements Scope {
    final ContextualScopeManager manager;
    final AtomicInteger refCount;
    private final Span wrapped;
    private final Scope toRestore;

    RefCountingScope(
        final ContextualScopeManager manager, final AtomicInteger refCount, final Span wrapped) {
      this.manager = manager;
      this.refCount = refCount;
      this.wrapped = wrapped;
      this.toRestore = manager.tlsScope.get();
      manager.tlsScope.set(this);
    }

    public class Continuation {
      public Continuation() {
        refCount.incrementAndGet();
      }

      public RefCountingScope activate() {
        return new RefCountingScope(manager, refCount, wrapped);
      }
    }

    public RefCountingScope.Continuation capture() {
      return new RefCountingScope.Continuation();
    }

    @Override
    public void close() {
      if (manager.tlsScope.get() != this) {
        return;
      }

      if (refCount.decrementAndGet() == 0) {
        wrapped.finish();
      }

      manager.tlsScope.set(toRestore);
    }

    @Override
    public Span span() {
      return wrapped;
    }
  }
}
