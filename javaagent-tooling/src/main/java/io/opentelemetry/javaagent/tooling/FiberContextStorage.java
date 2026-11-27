/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Objects.requireNonNull;
import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.ContextState;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class FiberContextStorage implements ContextStorage, AutoCloseable {

  private static final Logger logger = Logger.getLogger(FiberContextStorage.class.getName());

  private final ContextStorage delegate;
  private final boolean strictContextEnabled;
  private final AtomicInteger openFiberScopes = new AtomicInteger();

  FiberContextStorage(ContextStorage delegate) {
    this(delegate, isStrictContextEnabled());
  }

  // visible for testing
  FiberContextStorage(ContextStorage delegate, boolean strictContextEnabled) {
    this.delegate = delegate;
    this.strictContextEnabled = strictContextEnabled;
  }

  @Override
  public Scope attach(Context toAttach) {
    ContextState contextState = currentFiberContext();
    if (contextState == null) {
      return delegate.attach(toAttach);
    }

    Context previousContext = requireNonNull((Context) contextState.getContext());
    if (toAttach == previousContext && !strictContextEnabled) {
      return Scope.noop();
    }

    Object previousScope = contextState.getScope();
    FiberScope scope = new FiberScope(contextState, previousContext, previousScope);
    contextState.setContext(toAttach);
    contextState.setScope(scope);
    if (strictContextEnabled) {
      openFiberScopes.incrementAndGet();
    }
    return scope;
  }

  @Override
  @Nullable
  public Context current() {
    ContextState contextState = currentFiberContext();
    if (contextState == null) {
      return delegate.current();
    }
    return requireNonNull((Context) contextState.getContext());
  }

  @Override
  public Context root() {
    return delegate.root();
  }

  @Override
  public void close() throws Exception {
    if (openFiberScopes.get() != 0) {
      throw new AssertionError("Fiber context scopes were not closed");
    }
    if (delegate instanceof AutoCloseable) {
      ((AutoCloseable) delegate).close();
    }
  }

  @Nullable
  private static ContextState currentFiberContext() {
    return FiberContextStorageHolder.isEnabled() ? FiberContextStorageHolder.current() : null;
  }

  private static boolean isStrictContextEnabled() {
    return Boolean.getBoolean(ContextStorage.class.getPackage().getName() + ".enableStrictContext");
  }

  private final class FiberScope implements Scope {

    private final ContextState owner;
    private final Context previousContext;
    @Nullable private final Object previousScope;
    private volatile boolean closed;

    private FiberScope(
        ContextState owner, Context previousContext, @Nullable Object previousScope) {
      this.owner = owner;
      this.previousContext = previousContext;
      this.previousScope = previousScope;
    }

    @Override
    public void close() {
      if (closed) {
        return;
      }
      if (FiberContextStorageHolder.current() != owner) {
        if (strictContextEnabled) {
          throw new IllegalStateException("Fiber context scope closed by a different fiber");
        }
        logIgnoredInvalidClose("Trying to close fiber context scope from a different fiber.");
        return;
      }
      if (owner.getScope() != this) {
        if (strictContextEnabled) {
          throw new IllegalStateException("Fiber context scopes must be closed in reverse order");
        }
        logIgnoredInvalidClose("Trying to close fiber context scope out of order.");
        return;
      }

      owner.setContext(previousContext);
      owner.setScope(previousScope);
      closed = true;
      if (strictContextEnabled) {
        openFiberScopes.decrementAndGet();
      }
    }

    private void logIgnoredInvalidClose(String message) {
      try {
        logger.log(FINE, message + " Ignoring the call.");
      } catch (RuntimeException ignored) {
        // Logging must not change the non-throwing behavior of invalid closes.
      }
    }
  }
}
