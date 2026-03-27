/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rmi;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

public final class ThreadLocalContext {
  public static final ThreadLocalContext THREAD_LOCAL_CONTEXT = new ThreadLocalContext();
  private final ThreadLocal<Context> local;

  private ThreadLocalContext() {
    local = new ThreadLocal<>();
  }

  public void set(@Nullable Context context) {
    if (context == null) {
      local.remove();
    } else {
      local.set(context);
    }
  }

  @Nullable
  public Context getAndResetContext() {
    Context context = local.get();
    local.remove();
    return context;
  }
}
