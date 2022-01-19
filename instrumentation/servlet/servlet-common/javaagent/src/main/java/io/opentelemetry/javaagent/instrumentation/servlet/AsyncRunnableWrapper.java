/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

public class AsyncRunnableWrapper<REQUEST> implements Runnable {
  private final ServletHelper<REQUEST, ?> helper;
  private final REQUEST request;
  private final Runnable runnable;

  private AsyncRunnableWrapper(
      ServletHelper<REQUEST, ?> helper, REQUEST request, Runnable runnable) {
    this.helper = helper;
    this.request = request;
    this.runnable = runnable;
  }

  public static <REQUEST> Runnable wrap(
      ServletHelper<REQUEST, ?> helper, REQUEST request, Runnable runnable) {
    if (runnable == null || runnable instanceof AsyncRunnableWrapper) {
      return runnable;
    }
    return new AsyncRunnableWrapper<>(helper, request, runnable);
  }

  @Override
  public void run() {
    try {
      runnable.run();
    } catch (Throwable throwable) {
      helper.recordAsyncException(request, throwable);
      throw throwable;
    }
  }
}
