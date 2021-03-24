/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.service;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.servlet.jakarta.v5_0.JakartaServletHttpServerTracer;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicBoolean;

public class TagSettingAsyncListener implements AsyncListener {
  private static final JakartaServletHttpServerTracer tracer =
      JakartaServletHttpServerTracer.tracer();

  private final AtomicBoolean responseHandled;
  private final Context context;

  public TagSettingAsyncListener(AtomicBoolean responseHandled, Context context) {
    this.responseHandled = responseHandled;
    this.context = context;
  }

  @Override
  public void onComplete(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.end(context, (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  @Override
  public void onTimeout(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.onTimeout(context, event.getAsyncContext().getTimeout());
    }
  }

  @Override
  public void onError(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.endExceptionally(
          context, event.getThrowable(), (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  /** Transfer the listener over to the new context. */
  @Override
  public void onStartAsync(AsyncEvent event) {
    event.getAsyncContext().addListener(this);
  }
}
