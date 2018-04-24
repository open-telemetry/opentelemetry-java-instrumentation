package datadog.trace.instrumentation.ratpack.impl;

import datadog.opentracing.scopemanager.ScopeContext;
import io.opentracing.Scope;
import io.opentracing.Span;
import ratpack.exec.Execution;
import ratpack.exec.UnmanagedThreadException;

/**
 * This scope manager uses the Ratpack Execution to store the current Scope. This is a ratpack
 * registry analogous to a ThreadLocal but for an execution that may transfer between several
 * threads
 */
public final class RatpackScopeManager implements ScopeContext {
  @Override
  public boolean inContext() {
    return Execution.isManagedThread();
  }

  @Override
  public Scope activate(Span span, boolean finishSpanOnClose) {
    Execution execution = Execution.current();
    RatpackScope ratpackScope =
        new RatpackScope(
            span, finishSpanOnClose, execution.maybeGet(RatpackScope.class).orElse(null));
    // remove any existing RatpackScopes before adding it to the registry
    execution
        .maybeGet(RatpackScope.class)
        .ifPresent(ignored -> execution.remove(RatpackScope.class));
    execution.add(RatpackScope.class, ratpackScope);
    execution.onComplete(
        ratpackScope); // ensure that the scope is closed when the execution finishes
    return ratpackScope;
  }

  @Override
  public Scope active() {
    try {
      return Execution.current().maybeGet(RatpackScope.class).orElse(null);
    } catch (UnmanagedThreadException ume) {
      return null; // should never happen due to inContextCheck
    }
  }

  static class RatpackScope implements Scope {
    private final Span wrapped;
    private final boolean finishOnClose;
    private final RatpackScope toRestore;

    RatpackScope(Span wrapped, boolean finishOnClose, RatpackScope toRestore) {
      this.wrapped = wrapped;
      this.finishOnClose = finishOnClose;
      this.toRestore = toRestore;
    }

    @Override
    public Span span() {
      return wrapped;
    }

    @Override
    public void close() {
      Execution execution = Execution.current();
      // only close if this scope is the current scope for this Execution
      // As with ThreadLocalScope this shouldn't happen if users call methods in the expected order
      execution
          .maybeGet(RatpackScope.class)
          .filter(s -> this == s)
          .ifPresent(
              ignore -> {
                if (finishOnClose) {
                  wrapped.finish();
                }
                // pop the execution "stack"
                execution.remove(RatpackScope.class);
                if (toRestore != null) {
                  execution.add(toRestore);
                }
              });
    }
  }
}
