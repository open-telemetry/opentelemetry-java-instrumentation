package datadog.opentracing.scopemanager;

import datadog.opentracing.DDSpan;
import datadog.opentracing.DDSpanContext;
import datadog.opentracing.PendingTrace;
import datadog.trace.context.TraceScope;
import io.opentracing.Scope;
import java.io.Closeable;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContinuableScope implements Scope, TraceScope {
  /** ScopeManager holding the thread-local to this scope. */
  private final ContextualScopeManager scopeManager;
  /**
   * Span contained by this scope. Async scopes will hold a reference to the parent scope's span.
   */
  private final DDSpan spanUnderScope;
  /** If true, finish the span when openCount hits 0. */
  private final boolean finishOnClose;
  /** Count of open scope and continuations */
  private final AtomicInteger openCount;
  /** Scope to placed in the thread local after close. May be null. */
  private final Scope toRestore;
  /** Continuation that created this scope. May be null. */
  private final Continuation continuation;

  ContinuableScope(
      final ContextualScopeManager scopeManager,
      final DDSpan spanUnderScope,
      final boolean finishOnClose) {
    this(scopeManager, new AtomicInteger(1), null, spanUnderScope, finishOnClose);
  }

  private ContinuableScope(
      final ContextualScopeManager scopeManager,
      final AtomicInteger openCount,
      final Continuation continuation,
      final DDSpan spanUnderScope,
      final boolean finishOnClose) {
    this.scopeManager = scopeManager;
    this.openCount = openCount;
    this.continuation = continuation;
    this.spanUnderScope = spanUnderScope;
    this.finishOnClose = finishOnClose;
    this.toRestore = scopeManager.tlsScope.get();
    scopeManager.tlsScope.set(this);
  }

  @Override
  public void close() {
    if (null != continuation) {
      spanUnderScope.context().getTrace().cancelContinuation(continuation);
    }

    if (openCount.decrementAndGet() == 0 && finishOnClose) {
      spanUnderScope.finish();
    }

    if (scopeManager.tlsScope.get() == this) {
      scopeManager.tlsScope.set(toRestore);
    }
  }

  @Override
  public DDSpan span() {
    return spanUnderScope;
  }

  /**
   * The continuation returned should be closed after the associa
   *
   * @param finishOnClose
   * @return
   */
  public Continuation capture() {
    return new Continuation();
  }

  public class Continuation implements Closeable, TraceScope.Continuation {
    public WeakReference<Continuation> ref;

    private final AtomicBoolean used = new AtomicBoolean(false);
    private final PendingTrace trace;

    private Continuation() {
      openCount.incrementAndGet();
      final DDSpanContext context = (DDSpanContext) spanUnderScope.context();
      trace = context.getTrace();
      trace.registerContinuation(this);
    }

    public ContinuableScope activate() {
      if (used.compareAndSet(false, true)) {
        return new ContinuableScope(scopeManager, openCount, this, spanUnderScope, finishOnClose);
      } else {
        log.debug(
            "Failed to activate continuation. Reusing a continuation not allowed.  Returning a new scope. Spans will not be linked.");
        return new ContinuableScope(
            scopeManager, new AtomicInteger(1), null, spanUnderScope, finishOnClose);
      }
    }

    @Override
    public void close() {
      if (used.compareAndSet(false, true)) {
        trace.cancelContinuation(this);
        ContinuableScope.this.close();
      } else {
        log.debug("Failed to close continuation {}. Already used.", this);
      }
    }
  }
}
