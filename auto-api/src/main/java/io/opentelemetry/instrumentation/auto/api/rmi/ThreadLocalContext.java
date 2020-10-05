/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.api.rmi;

import io.grpc.Context;

public class ThreadLocalContext {
  public static final ThreadLocalContext THREAD_LOCAL_CONTEXT = new ThreadLocalContext();
  private final ThreadLocal<Context> local;

  public ThreadLocalContext() {
    local = new ThreadLocal<>();
  }

  public void set(Context context) {
    local.set(context);
  }

  public Context getAndResetContext() {
    Context context = local.get();
    if (context != null) {
      local.remove();
    }
    return context;
  }
}
