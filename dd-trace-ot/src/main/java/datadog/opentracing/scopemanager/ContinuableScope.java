package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.opentracing.DDSpanContext;
import datadog.opentracing.PendingTrace;
import datadog.opentracing.jfr.DDScopeEvent;
import datadog.opentracing.jfr.DDScopeEventFactory;
import datadog.trace.context.ScopeListener;
import datadog.trace.context.TraceScope;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContinuableScope implements DDScope, TraceScope {
  /** ScopeManager holding the thread-local to this scope. */
  private final ContextualScopeManager scopeManager;
  /**
   * Span contained by this scope. Async scopes will hold a reference to the parent scope's span.
   */
  private final DDSpan spanUnderScope;

  private final DDScopeEventFactory eventFactory;
  /** Event for this scope */
  private final DDScopeEvent event;
  /** If true, finish the span when openCount hits 0. */
  private final boolean finishOnClose;
  /** Count of open scope and continuations */
  private final AtomicInteger openCount;
  /** Scope to placed in the thread local after close. May be null. */
  private final DDScope toRestore;
  /** Continuation that created this scope. May be null. */
  private final Continuation continuation;
  /** Flag to propagate this scope across async boundaries. */
  private final AtomicBoolean isAsyncPropagating = new AtomicBoolean(false);
  /** depth of scope on thread */
  private final int depth;

  ContinuableScope(
      final ContextualScopeManager scopeManager,
      final DDSpan spanUnderScope,
      final boolean finishOnClose,
      final DDScopeEventFactory eventFactory) {
    this(scopeManager, new AtomicInteger(1), null, spanUnderScope, finishOnClose, eventFactory);
  }

  private ContinuableScope(
      final ContextualScopeManager scopeManager,
      final AtomicInteger openCount,
      final Continuation continuation,
      final DDSpan spanUnderScope,
      final boolean finishOnClose,
      final DDScopeEventFactory eventFactory) {
    assert spanUnderScope != null : "span must not be null";
    this.scopeManager = scopeManager;
    this.openCount = openCount;
    this.continuation = continuation;
    this.spanUnderScope = spanUnderScope;
    this.finishOnClose = finishOnClose;
    this.eventFactory = eventFactory;
    event = eventFactory.create(spanUnderScope.context());
    event.start();
    toRestore = scopeManager.tlsScope.get();
    scopeManager.tlsScope.set(this);
    depth = toRestore == null ? 0 : toRestore.depth() + 1;
    for (final ScopeListener listener : scopeManager.scopeListeners) {
      listener.afterScopeActivated();
    }
  }

  @Override
  public void close() {
    // We have to scope finish event before we finish then span (which finishes span event).
    // The reason is that we get span on construction and span event starts when span is created.
    // This means from JFR perspective scope is included into the span.
    event.finish();

    if (null != continuation) {
      spanUnderScope.context().getTrace().cancelContinuation(continuation);
    }

    if (openCount.decrementAndGet() == 0 && finishOnClose) {
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
  public int depth() {
    return depth;
  }

  @Override
  public boolean isAsyncPropagating() {
    return isAsyncPropagating.get();
  }

  @Override
  public void setAsyncPropagation(final boolean value) {
    isAsyncPropagating.set(value);
  }

  /**
   * The continuation returned must be closed or activated or the trace will not finish.
   *
   * @return The new continuation, or null if this scope is not async propagating.
   */
  @Override
  public Continuation capture() {
    if (isAsyncPropagating()) {
      return new Continuation();
    } else {
      return null;
    }
  }

  @Override
  public String toString() {
    return super.toString() + "->" + spanUnderScope;
  }

  public class Continuation implements Closeable, TraceScope.Continuation {
    public WeakReference<Continuation> ref;

    private final AtomicBoolean used = new AtomicBoolean(false);
    private final PendingTrace trace;

    private Continuation() {
      openCount.incrementAndGet();
      final DDSpanContext context = spanUnderScope.context();
      trace = context.getTrace();
      trace.registerContinuation(this);
    }

    @Override
    public ContinuableScope activate() {
      if (used.compareAndSet(false, true)) {
        final ContinuableScope scope =
            new ContinuableScope(
                scopeManager, openCount, this, spanUnderScope, finishOnClose, eventFactory);
        log.debug("Activating continuation {}, scope: {}", this, scope);
        return scope;
      } else {
        log.debug(
            "Failed to activate continuation. Reusing a continuation not allowed.  Returning a new scope. Spans will not be linked.");
        return new ContinuableScope(
            scopeManager, new AtomicInteger(1), null, spanUnderScope, finishOnClose, eventFactory);
      }
    }

    @Override
    public void close() {
      close(true);
    }

    @Override
    public void close(final boolean closeContinuationScope) {
      if (used.compareAndSet(false, true)) {
        trace.cancelContinuation(this);
        if (closeContinuationScope) {
          ContinuableScope.this.close();
        } else {
          // Same as in 'close()' above.
          if (openCount.decrementAndGet() == 0 && finishOnClose) {
            spanUnderScope.finish();
          }
        }
      } else {
        log.debug("Failed to close continuation {}. Already used.", this);
      }
    }
  }
}
