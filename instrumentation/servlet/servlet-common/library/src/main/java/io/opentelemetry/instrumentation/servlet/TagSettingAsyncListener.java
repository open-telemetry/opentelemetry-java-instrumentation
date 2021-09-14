/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.context.Context;
import java.util.concurrent.atomic.AtomicBoolean;

@Deprecated
public class TagSettingAsyncListener<REQUEST, RESPONSE> implements ServletAsyncListener<RESPONSE> {
  private final ServletHttpServerTracer<REQUEST, RESPONSE> tracer;
  private final AtomicBoolean responseHandled;
  private final Context context;

  public TagSettingAsyncListener(
      ServletHttpServerTracer<REQUEST, RESPONSE> tracer,
      AtomicBoolean responseHandled,
      Context context) {
    this.tracer = tracer;
    this.responseHandled = responseHandled;
    this.context = context;
  }

  @Override
  public void onComplete(RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.end(context, response);
    }
  }

  @Override
  public void onTimeout(long timeout) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.onTimeout(context, timeout);
    }
  }

  @Override
  public void onError(Throwable throwable, RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      tracer.endExceptionally(context, throwable, response);
    }
  }
}
