/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncRequestCompletionListener<REQUEST, RESPONSE>
    implements ServletAsyncListener<RESPONSE> {
  private final ServletHelper<REQUEST, RESPONSE> servletHelper;
  private final Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
      instrumenter;
  private final ServletRequestContext<REQUEST> requestContext;
  private final Context context;
  private final AtomicBoolean responseHandled = new AtomicBoolean();

  public AsyncRequestCompletionListener(
      ServletHelper<REQUEST, RESPONSE> servletHelper,
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> instrumenter,
      ServletRequestContext<REQUEST> requestContext,
      Context context) {
    // The context passed into this method may contain other spans besides the server span. To end
    // the server span we get the context that set at the start of the request with
    // ServletHelper#setAsyncListenerResponse that contains just the server span.
    Context serverSpanContext = servletHelper.getAsyncListenerContext(context);
    this.servletHelper = servletHelper;
    this.instrumenter = instrumenter;
    this.requestContext = requestContext;
    this.context = serverSpanContext != null ? serverSpanContext : context;
  }

  @Override
  public void onComplete(RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
      Throwable throwable = servletHelper.getAsyncException(context);
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }

  @Override
  public void onTimeout(long timeout) {
    if (responseHandled.compareAndSet(false, true)) {
      RESPONSE response = servletHelper.getAsyncListenerResponse(context);
      ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
      responseContext.setTimeout(timeout);
      Throwable throwable = servletHelper.getAsyncException(context);
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }

  @Override
  public void onError(Throwable throwable, RESPONSE response) {
    if (responseHandled.compareAndSet(false, true)) {
      ServletResponseContext<RESPONSE> responseContext = new ServletResponseContext<>(response);
      instrumenter.end(context, requestContext, responseContext, throwable);
    }
  }
}
