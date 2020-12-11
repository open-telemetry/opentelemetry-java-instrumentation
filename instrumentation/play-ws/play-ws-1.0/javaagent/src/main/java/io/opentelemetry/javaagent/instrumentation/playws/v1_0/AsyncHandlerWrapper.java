/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v1_0;

import static io.opentelemetry.javaagent.instrumentation.playws.PlayWsClientTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Response;

public class AsyncHandlerWrapper<T> implements AsyncHandler<T> {
  private final AsyncHandler<T> delegate;
  private final Context context;
  private final Context parentContext;

  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

  public AsyncHandlerWrapper(AsyncHandler delegate, Context context, Context parentContext) {
    this.delegate = delegate;
    this.context = context;
    this.parentContext = parentContext;
  }

  @Override
  public State onBodyPartReceived(HttpResponseBodyPart content) throws Exception {
    builder.accumulate(content);
    return delegate.onBodyPartReceived(content);
  }

  @Override
  public State onStatusReceived(HttpResponseStatus status) throws Exception {
    builder.reset();
    builder.accumulate(status);
    return delegate.onStatusReceived(status);
  }

  @Override
  public State onHeadersReceived(HttpResponseHeaders httpHeaders) throws Exception {
    builder.accumulate(httpHeaders);
    return delegate.onHeadersReceived(httpHeaders);
  }

  @Override
  public T onCompleted() throws Exception {
    tracer().end(context, builder.build());
    try (Scope ignored = parentContext.makeCurrent()) {
      return delegate.onCompleted();
    }
  }

  @Override
  public void onThrowable(Throwable throwable) {
    tracer().endExceptionally(context, throwable);
    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.onThrowable(throwable);
    }
  }
}
