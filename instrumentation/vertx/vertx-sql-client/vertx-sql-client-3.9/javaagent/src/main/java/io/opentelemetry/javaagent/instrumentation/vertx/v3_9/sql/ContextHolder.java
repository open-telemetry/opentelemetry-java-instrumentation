/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.sql;

import io.opentelemetry.context.Context;

public class ContextHolder {
  private static final ThreadLocal<Context> contextHolder = new ThreadLocal<>();

  private ContextHolder() {}

  public static void set(Context context) {
    contextHolder.set(context);
  }

  public static Context get() {
    Context context = contextHolder.get();
    return context != null ? context : Context.root();
  }

  public static void clear() {
    contextHolder.remove();
  }
}
