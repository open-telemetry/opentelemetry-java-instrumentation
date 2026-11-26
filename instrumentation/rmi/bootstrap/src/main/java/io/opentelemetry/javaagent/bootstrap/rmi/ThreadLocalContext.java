/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rmi;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class ThreadLocalContext {
  public static final ThreadLocalContext INSTANCE = new ThreadLocalContext();
  private final ThreadLocal<Context> pendingParentContext;

  private ThreadLocalContext() {
    pendingParentContext = new ThreadLocal<>();
  }

  public void set(@Nullable Context context) {
    if (context == null) {
      pendingParentContext.remove();
    } else {
      pendingParentContext.set(context);
    }
  }

  @Nullable
  public Context getAndResetContext() {
    Context context = pendingParentContext.get();
    pendingParentContext.remove();
    return context;
  }
}
