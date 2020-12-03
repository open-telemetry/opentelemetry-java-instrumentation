/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v3_0;

import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.http.HttpServletResponse;

public class TagSettingAsyncListener implements AsyncListener {
  private static final Servlet3HttpServerTracer servletHttpServerTracer =
      new Servlet3HttpServerTracer();

  private final AtomicBoolean responseHandled;
  private final Context context;

  public TagSettingAsyncListener(AtomicBoolean responseHandled, Context context) {
    this.responseHandled = responseHandled;
    this.context = context;
  }

  @Override
  public void onComplete(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.end(context, (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  @Override
  public void onTimeout(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.onTimeout(context, event.getAsyncContext().getTimeout());
    }
  }

  @Override
  public void onError(AsyncEvent event) {
    if (responseHandled.compareAndSet(false, true)) {
      servletHttpServerTracer.endExceptionally(
          context, event.getThrowable(), (HttpServletResponse) event.getSuppliedResponse());
    }
  }

  /** Transfer the listener over to the new context. */
  @Override
  public void onStartAsync(AsyncEvent event) {
    event.getAsyncContext().addListener(this);
  }
}
