/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws.v1_0;

import static io.opentelemetry.javaagent.instrumentation.playws.PlayWsClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.HttpResponseBodyPart;
import play.shaded.ahc.org.asynchttpclient.HttpResponseHeaders;
import play.shaded.ahc.org.asynchttpclient.HttpResponseStatus;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class AsyncHandlerWrapper implements AsyncHandler {
  private final AsyncHandler delegate;
  private final Request request;
  private final Context context;
  private final Context parentContext;

  private final Response.ResponseBuilder builder = new Response.ResponseBuilder();

  public AsyncHandlerWrapper(
      AsyncHandler delegate, Request request, Context context, Context parentContext) {
    this.delegate = delegate;
    this.request = request;
    this.context = context;
    this.parentContext = parentContext;
  }

  public Context getParentContext() {
    return parentContext;
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
  public Object onCompleted() throws Exception {
    instrumenter().end(context, request, builder.build(), null);

    try (Scope ignored = parentContext.makeCurrent()) {
      return delegate.onCompleted();
    }
  }

  @Override
  public void onThrowable(Throwable throwable) {
    instrumenter().end(context, request, null, throwable);

    try (Scope ignored = parentContext.makeCurrent()) {
      delegate.onThrowable(throwable);
    }
  }
}
