/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class JettyHttpClient12TracingInterceptor
    implements Request.BeginListener,
        Request.FailureListener,
        Response.SuccessListener,
        Response.FailureListener {

  private static final Logger logger =
      Logger.getLogger(JettyHttpClient12TracingInterceptor.class.getName());

  private final Context parentContext;

  @Nullable private Context context;

  private final Instrumenter<Request, Response> instrumenter;

  public JettyHttpClient12TracingInterceptor(
      Context parentCtx, Instrumenter<Request, Response> instrumenter) {
    this.parentContext = parentCtx;
    this.instrumenter = instrumenter;
  }

  @Nullable
  public Context getContext() {
    return this.context;
  }

  void startSpan(Request jettyRequest) {
    if (jettyRequest == null) {
      return;
    }
    if (!instrumenter.shouldStart(this.parentContext, jettyRequest)) {
      return;
    }
    this.context = instrumenter.start(this.parentContext, jettyRequest);
  }

  public void attachToRequest(Request jettyRequest) {
    startSpan(jettyRequest);
    jettyRequest
        .onRequestBegin(this)
        .onRequestFailure(this)
        .onResponseFailure(this)
        .onResponseSuccess(this);
  }

  void closeIfPossible(Response response) {
    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, null);
    } else {
      logger.fine("onComplete - could not find an otel context");
    }
  }

  @Override
  public void onBegin(Request request) {}

  @Override
  public void onFailure(Response response, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, t);
    }
  }

  @Override
  public void onFailure(Request request, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, request, null, t);
    }
  }

  @Override
  public void onSuccess(Response response) {
    closeIfPossible(response);
  }
}
