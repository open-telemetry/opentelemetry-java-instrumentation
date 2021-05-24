/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_0.JettyHttpClient9Tracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JettyHttpClient9TracingInterceptor
    implements Request.BeginListener,
        Request.FailureListener,
        Response.SuccessListener,
        Response.FailureListener,
        Response.CompleteListener,
        Request.CommitListener {

  private static final Logger LOG =
      LoggerFactory.getLogger(JettyHttpClient9TracingInterceptor.class);

  private @Nullable Context ctx;

  @Nullable
  public Context getCtx() {
    return ctx;
  }

  private final Context parentContext;

  public JettyHttpClient9TracingInterceptor(Context parentCtx) {
    this.parentContext = parentCtx;
  }

  public void attachToRequest(Request jettyRequest) {
    List<JettyHttpClient9TracingInterceptor> current =
        jettyRequest.getRequestListeners(JettyHttpClient9TracingInterceptor.class);

    if (!current.isEmpty()) {
      LOG.warn("A tracing interceptor is already in place for this request! ");
      return;
    }

    jettyRequest
        .onRequestBegin(this)
        .onRequestFailure(this)
        .onResponseFailure(this)
        .onResponseSuccess(this)
        .onRequestCommit(this);

    startSpan(jettyRequest);
  }

  private void startSpan(Request request) {

    if (this.parentContext != null) {
      if (!tracer().shouldStartSpan(this.parentContext)) {
        return;
      }
      Context context = tracer().startSpan(parentContext, request, request);
      this.ctx = context;

    } else {
      LOG.warn("StartSpan - could not find an otel context");
    }
  }

  @Override
  public void onBegin(Request request) {
    if (this.ctx != null) {
      Span span = Span.fromContext(this.ctx);
      tracer().onRequest(span, request);
      tracer().updateSpanName(span, request);

      try (Scope scope = span.makeCurrent()) {}
    }
  }

  @Override
  public void onFailure(Request request, Throwable t) {
    if (this.ctx != null) {
      tracer().endExceptionally(this.ctx, t);
    }
  }

  @Override
  public void onComplete(Result result) {
    closeIfPossible(result.getResponse());
  }

  @Override
  public void onSuccess(Response response) {
    closeIfPossible(response);
  }

  @Override
  public void onFailure(Response response, Throwable t) {
    if (this.ctx != null) {
      tracer().endExceptionally(this.ctx, t);
    }
  }

  private void closeIfPossible(Response response) {

    if (this.ctx != null) {
      tracer().end(this.ctx, response);
    } else {
      LOG.warn("onComplete - could not find an otel context");
    }
  }

  @Override
  public void onCommit(Request request) {
    // Doing nothing here yet;
  }
}
