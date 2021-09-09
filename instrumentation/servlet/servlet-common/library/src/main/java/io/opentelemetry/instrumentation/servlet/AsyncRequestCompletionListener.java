/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncRequestCompletionListener<REQUEST, RESPONSE>
    implements ServletAsyncListener<RESPONSE> {
  private final Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
      instrumenter;
  private final ServletRequestContext<REQUEST> requestContext;
  private final Context context;
  private final AtomicBoolean responseHandled = new AtomicBoolean();

  public AsyncRequestCompletionListener(
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletRequestContext<REQUEST> requestContext,
      Context context) {
    this.instrumenter = instrumenter;
    this.requestContext = requestContext;
    this.context = context;
  }

  @Override
  public void onComplete(RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      ServletResponseContext<RESPONSE> responseContext =
          new ServletResponseContext<>(response, null);
      instrumenter.end(context, requestContext, responseContext, null);
    }
  }

  @Override
  public void onTimeout(long timeout) {
    if (responseHandled.compareAndSet(false, true)) {
      Span span = Span.fromContext(context);
      span.setStatus(StatusCode.ERROR);
      if (ServletHttpServerTracer.CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        span.setAttribute("servlet.timeout", timeout);
      }
      span.end();
    }
  }

  @Override
  public void onError(Throwable throwable, RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      ServletResponseContext<RESPONSE> responseContext =
          new ServletResponseContext<>(response, throwable);
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }
}
