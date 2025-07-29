/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

/**
 * JettyClientTracingListener performs two actions when {@link #handleRequest(Context, Request,
 * Instrumenter)} is called 1. Start the CLIENT span 2. Set the listener callbacks for each
 * lifecycle action that signal end of the request.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class JettyClientTracingListener
    implements Request.FailureListener, Response.SuccessListener, Response.FailureListener {

  private final Context context;

  private final Instrumenter<Request, Response> instrumenter;

  private JettyClientTracingListener(
      Context context, Instrumenter<Request, Response> instrumenter) {
    this.context = context;
    this.instrumenter = instrumenter;
  }

  @Nullable
  public static Context handleRequest(
      Context parentContext, Request jettyRequest, Instrumenter<Request, Response> instrumenter) {
    if (!instrumenter.shouldStart(parentContext, jettyRequest)) {
      return null;
    }

    Context context = instrumenter.start(parentContext, jettyRequest);

    JettyClientTracingListener listener = new JettyClientTracingListener(context, instrumenter);
    jettyRequest.onRequestFailure(listener).onResponseFailure(listener).onResponseSuccess(listener);
    return context;
  }

  @Override
  public void onFailure(Response response, Throwable t) {
    instrumenter.end(this.context, response.getRequest(), response, t);
  }

  @Override
  public void onFailure(Request request, Throwable t) {
    instrumenter.end(this.context, request, null, t);
  }

  @Override
  public void onSuccess(Response response) {
    instrumenter.end(this.context, response.getRequest(), response, null);
  }
}
