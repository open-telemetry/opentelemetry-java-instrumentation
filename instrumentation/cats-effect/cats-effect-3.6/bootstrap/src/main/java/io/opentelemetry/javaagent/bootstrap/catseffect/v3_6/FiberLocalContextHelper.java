/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.catseffect.v3_6;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** The helper stores a reference to the IOLocal#unsafeThreadLocal. */
public final class FiberLocalContextHelper {

  private static final Logger logger = Logger.getLogger(FiberLocalContextHelper.class.getName());

  private static final AtomicReference<ThreadLocal<Context>> fiberContextThreadLocal =
      new AtomicReference<>();

  private static final AtomicReference<Supplier<Boolean>> isUnderFiberContextSupplier =
      new AtomicReference<>(() -> false);

  public static void initialize(
      ThreadLocal<Context> fiberThreadLocal, Supplier<Boolean> isUnderFiberContext) {
    if (fiberContextThreadLocal.get() == null) {
      fiberContextThreadLocal.set(fiberThreadLocal);
      isUnderFiberContextSupplier.set(isUnderFiberContext);
      logger.fine("The fiberThreadLocalContext is configured");
    } else {
      if (!fiberContextThreadLocal.get().equals(fiberThreadLocal)) {
        logger.warning(
            "The fiberThreadLocalContext is already configured. Ignoring subsequent calls.");
      }
    }
  }

  public static Boolean isUnderFiberContext() {
    return isUnderFiberContextSupplier.get().get();
  }

  @Nullable
  public static Context current() {
    ThreadLocal<Context> local = getFiberThreadLocal();
    return local != null ? local.get() : null;
  }

  public static Scope attach(Context toAttach) {
    ThreadLocal<Context> local = fiberContextThreadLocal.get();
    if (toAttach == null || local == null) {
      return Scope.noop();
    } else {
      Context beforeAttach = current();
      if (toAttach == beforeAttach) {
        return Scope.noop();
      } else {
        local.set(toAttach);
        return new ScopeImpl(beforeAttach, toAttach);
      }
    }
  }

  @Nullable
  private static ThreadLocal<Context> getFiberThreadLocal() {
    return fiberContextThreadLocal.get();
  }

  private static class ScopeImpl implements Scope {
    @Nullable private final Context beforeAttach;
    private final Context toAttach;
    private boolean closed;

    private ScopeImpl(@Nullable Context beforeAttach, Context toAttach) {
      this.beforeAttach = beforeAttach;
      this.toAttach = toAttach;
    }

    @Override
    public void close() {
      if (!this.closed && FiberLocalContextHelper.current() == this.toAttach) {
        this.closed = true;
        FiberLocalContextHelper.fiberContextThreadLocal.get().set(this.beforeAttach);
      } else {
        FiberLocalContextHelper.logger.log(
            Level.FINE,
            "Trying to close scope which does not represent current context. Ignoring the call.");
      }
    }
  }

  private FiberLocalContextHelper() {}
}
