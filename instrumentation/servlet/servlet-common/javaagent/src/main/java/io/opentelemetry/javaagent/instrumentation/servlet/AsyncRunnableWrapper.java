/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.Context;

public class AsyncRunnableWrapper<REQUEST> implements Runnable {
  private final ServletHelper<REQUEST, ?> helper;
  private final Runnable runnable;
  private final Context context;

  private AsyncRunnableWrapper(ServletHelper<REQUEST, ?> helper, Runnable runnable) {
    this.helper = helper;
    this.runnable = runnable;
    this.context = Context.current();
  }

  public static <REQUEST> Runnable wrap(ServletHelper<REQUEST, ?> helper, Runnable runnable) {
    if (runnable == null || runnable instanceof AsyncRunnableWrapper) {
      return runnable;
    }
    return new AsyncRunnableWrapper<>(helper, runnable);
  }

  @Override
  public void run() {
    try {
      runnable.run();
    } catch (Throwable throwable) {
      helper.recordAsyncException(context, throwable);
      throw throwable;
    }
  }
}
