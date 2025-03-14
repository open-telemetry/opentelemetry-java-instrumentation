/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.otel4s;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** The helper stores a reference to the IOLocal#unsafeThreadLocal. */
public final class FiberLocalContextHelper {

  private static final Logger logger = Logger.getLogger(FiberLocalContextHelper.class.getName());

  private static final AtomicReference<ThreadLocal<Context>> fiberContextThreadLocal =
      new AtomicReference<>();

  public static void setFiberThreadLocalContext(ThreadLocal<Context> fiberThreadLocal) {
    if (fiberContextThreadLocal.get() == null) {
      fiberContextThreadLocal.set(fiberThreadLocal);
      logger.fine("The fiberThreadLocalContext is configured");
    } else {
      if (!fiberContextThreadLocal.get().equals(fiberThreadLocal)) {
        logger.warning(
            "The fiberThreadLocalContext is already configured. Ignoring subsequent calls.");
      }
    }
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
        return () -> local.set(beforeAttach);
      }
    }
  }

  @Nullable
  private static ThreadLocal<Context> getFiberThreadLocal() {
    return fiberContextThreadLocal.get();
  }

  private FiberLocalContextHelper() {}
}
