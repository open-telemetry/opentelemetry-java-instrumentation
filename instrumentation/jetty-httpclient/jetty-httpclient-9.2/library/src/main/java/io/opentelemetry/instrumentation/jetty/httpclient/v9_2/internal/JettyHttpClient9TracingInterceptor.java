/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JettyHttpClient9TracingInterceptor does three jobs stimulated from the Jetty Request object from
 * attachToRequest() 1. Start the CLIENT span and create the tracer 2. Set the listener callbacks
 * for each important lifecycle actions that would cause the start and close of the span 3. Set
 * callback wrappers on two important request-based callbacks
 */
public class JettyHttpClient9TracingInterceptor
    implements Request.BeginListener,
        Request.FailureListener,
        Response.SuccessListener,
        Response.FailureListener,
        Response.CompleteListener {

  private static final Logger logger =
      LoggerFactory.getLogger(JettyHttpClient9TracingInterceptor.class);

  private static final Class<?>[] requestlistenerInterfaces = {
    Request.BeginListener.class,
    Request.FailureListener.class,
    Request.SuccessListener.class,
    Request.HeadersListener.class,
    Request.ContentListener.class,
    Request.CommitListener.class,
    Request.QueuedListener.class
  };

  @Nullable private Context context;

  @Nullable
  public Context getContext() {
    return this.context;
  }

  private final Context parentContext;

  private final Instrumenter<Request, Response> instrumenter;

  public JettyHttpClient9TracingInterceptor(
      Context parentCtx, Instrumenter<Request, Response> instrumenter) {
    this.parentContext = parentCtx;
    this.instrumenter = instrumenter;
  }

  public void attachToRequest(Request jettyRequest) {
    List<JettyHttpClient9TracingInterceptor> current =
        jettyRequest.getRequestListeners(JettyHttpClient9TracingInterceptor.class);

    if (!current.isEmpty()) {
      logger.warn("A tracing interceptor is already in place for this request! ");
      return;
    }
    startSpan(jettyRequest);

    // wrap all important request-based listeners that may already be attached, null should ensure
    // are returned here
    List<Request.RequestListener> existingListeners = jettyRequest.getRequestListeners(null);
    wrapRequestListeners(existingListeners);

    jettyRequest
        .onRequestBegin(this)
        .onRequestFailure(this)
        .onResponseFailure(this)
        .onResponseSuccess(this);
  }

  private void wrapRequestListeners(List<Request.RequestListener> requestListeners) {

    ListIterator<Request.RequestListener> iterator = requestListeners.listIterator();

    while (iterator.hasNext()) {
      List<Class<?>> interfaces = new ArrayList<>();
      Request.RequestListener listener = iterator.next();

      Class<?> listenerClass = listener.getClass();

      for (Class<?> type : requestlistenerInterfaces) {
        if (type.isInstance(listener)) {
          interfaces.add(type);
        }
      }

      if (interfaces.isEmpty()) {
        continue;
      }

      Request.RequestListener proxiedListner =
          (Request.RequestListener)
              Proxy.newProxyInstance(
                  listenerClass.getClassLoader(),
                  interfaces.toArray(new Class[0]),
                  (proxy, method, args) -> {
                    try (Scope ignored = context.makeCurrent()) {
                      return method.invoke(listener, args);
                    }
                  });

      iterator.set(proxiedListner);
    }
  }

  private void startSpan(Request request) {

    if (!instrumenter.shouldStart(this.parentContext, request)) {
      return;
    }
    this.context = instrumenter.start(this.parentContext, request);
  }

  @Override
  public void onBegin(Request request) {
    if (this.context != null) {
      Span span = Span.fromContext(this.context);
      HttpField agentField = request.getHeaders().getField(HttpHeader.USER_AGENT);
      if (agentField != null) {
        span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, agentField.getValue());
      }
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
  public void onFailure(Request request, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, request, null, t);
    }
  }

  @Override
  public void onFailure(Response response, Throwable t) {
    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, t);
    }
  }

  private void closeIfPossible(Response response) {

    if (this.context != null) {
      instrumenter.end(this.context, response.getRequest(), response, null);
    } else {
      logger.debug("onComplete - could not find an otel context");
    }
  }
}
