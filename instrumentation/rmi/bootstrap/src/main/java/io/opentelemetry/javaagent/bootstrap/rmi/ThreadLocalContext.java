/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rmi;

import io.opentelemetry.context.Context;
import javax.annotation.Nullable;

@SuppressWarnings("ThreadLocalUsage")
public final class ThreadLocalContext {
  public static final ThreadLocalContext INSTANCE = new ThreadLocalContext();
  // ContextDispatcher publishes the payload for the next remote call on the same connection worker.
  // RemoteServerInstrumentation consumes it once; server dispatch and connection-handler exit clear
  // abandoned payloads before the worker is reused. A failed ContextDispatcher never publishes one.
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
