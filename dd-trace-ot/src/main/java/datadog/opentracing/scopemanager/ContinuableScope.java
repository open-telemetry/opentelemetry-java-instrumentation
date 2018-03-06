package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpanContext;
import datadog.opentracing.PendingTrace;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.noop.NoopScopeManager;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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

  /**
   * The continuation returned should be closed after the associa
   *
   * @param finishOnClose
   * @return
   */
  public Continuation capture(final boolean finishOnClose) {
    return new Continuation(this.finishOnClose && finishOnClose);
  }

  public class Continuation implements Closeable {
    public WeakReference<Continuation> ref;

    private final AtomicBoolean used = new AtomicBoolean(false);
    private final PendingTrace trace;
    private final boolean finishSpanOnClose;

    private Continuation(final boolean finishOnClose) {
      this.finishSpanOnClose = finishOnClose;
      refCount.incrementAndGet();
      if (wrapped.context() instanceof DDSpanContext) {
        final DDSpanContext context = (DDSpanContext) wrapped.context();
        trace = context.getTrace();
        trace.registerContinuation(this);
      } else {
        trace = null;
      }
    }

    public Scope activate() {
      if (used.compareAndSet(false, true)) {
        for (final ScopeContext context : scopeManager.scopeContexts) {
          if (context.inContext()) {
            return new ClosingScope(context.activate(wrapped, finishSpanOnClose));
          }
        }
        return new ClosingScope(
            new ContinuableScope(scopeManager, refCount, wrapped, finishSpanOnClose));
      } else {
        log.debug("Reusing a continuation not allowed.  Returning no-op scope.");
        return NoopScopeManager.NoopScope.INSTANCE;
      }
    }

    @Override
    public void close() {
      used.getAndSet(true);
      if (trace != null) {
        trace.cancelContinuation(this);
      }
    }

    private class ClosingScope implements Scope {
      private final Scope wrapped;

      private ClosingScope(final Scope wrapped) {
        this.wrapped = wrapped;
      }

      @Override
      public void close() {
        wrapped.close();
        Continuation.this.close();
      }

      @Override
      public Span span() {
        return wrapped.span();
      }
    }
  }
}
