/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextStorage;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder;
import io.opentelemetry.javaagent.bootstrap.FiberContextStorageHolder.ContextState;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;

final class FiberContextStorage implements ContextStorage, AutoCloseable {

  private final ContextStorage delegate;
  private final AtomicInteger openFiberScopes = new AtomicInteger();

  FiberContextStorage(ContextStorage delegate) {
    this.delegate = delegate;
  }

  @Override
  public Scope attach(Context toAttach) {
    ContextState contextState = currentFiberContext();
    if (contextState == null) {
      return delegate.attach(toAttach);
    }

    Context previousContext = requireNonNull((Context) contextState.getContext());
    Object previousScope = contextState.getScope();
    FiberScope scope = new FiberScope(contextState, previousContext, previousScope);
    contextState.setContext(toAttach);
    contextState.setScope(scope);
    openFiberScopes.incrementAndGet();
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

  private final class FiberScope implements Scope {

    private final ContextState owner;
    private final Context previousContext;
    @Nullable private final Object previousScope;
    private boolean closed;

    private FiberScope(
        ContextState owner, Context previousContext, @Nullable Object previousScope) {
      this.owner = owner;
      this.previousContext = previousContext;
      this.previousScope = previousScope;
    }

    @Override
    public synchronized void close() {
      if (closed) {
        return;
      }
      if (FiberContextStorageHolder.current() != owner) {
        throw new IllegalStateException("Fiber context scope closed by a different fiber");
      }
      if (owner.getScope() != this) {
        throw new IllegalStateException("Fiber context scopes must be closed in reverse order");
      }

      owner.setContext(previousContext);
      owner.setScope(previousScope);
      closed = true;
      openFiberScopes.decrementAndGet();
    }
  }
}
