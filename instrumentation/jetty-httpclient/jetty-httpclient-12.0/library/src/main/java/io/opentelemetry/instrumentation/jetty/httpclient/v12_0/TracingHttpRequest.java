/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientTracingListener;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;

class TracingHttpRequest extends HttpRequest {

  private static final Class<?>[] LISTENER_INTERFACES = {
    Response.CompleteListener.class,
    Response.FailureListener.class,
    Response.SuccessListener.class,
    Response.AsyncContentListener.class,
    Response.ContentSourceListener.class,
    Response.ContentListener.class,
    Response.HeadersListener.class,
    Response.HeaderListener.class,
    Response.BeginListener.class,
    Response.Listener.class
  };

  private final Instrumenter<Request, Response> instrumenter;
  @Nullable private Context callerContext;

  TracingHttpRequest(
      HttpClient client,
      HttpConversation conversation,
      URI uri,
      Instrumenter<Request, Response> instrumenter) {
    super(client, conversation, uri);
    this.instrumenter = instrumenter;
  }

  @Override
  public void send(Response.CompleteListener listener) {
    callerContext = Context.current();
    // start span and attach listeners - must handle all listeners, not just CompleteListener.
    JettyClientTracingListener.handleRequest(callerContext, this, instrumenter);
    super.send(wrapResponseListener(listener));
  }

  @Nullable
  private Scope openScope() {
    return callerContext != null ? callerContext.makeCurrent() : null;
  }

  @Nullable
  // The proxy implements the listener's interfaces, so it remains assignable to T.
  @SuppressWarnings("unchecked")
  private <T extends Response.ResponseListener> T wrapResponseListener(@Nullable T listener) {
    if (listener == null || listener instanceof JettyClientTracingListener) {
      return listener;
    }

    Class<?> listenerClass = listener.getClass();
    List<Class<?>> interfaces = new ArrayList<>();
    for (Class<?> type : LISTENER_INTERFACES) {
      if (type.isInstance(listener)) {
        interfaces.add(type);
      }
    }
    if (interfaces.isEmpty()) {
      return listener;
    }

    return (T)
        Proxy.newProxyInstance(
            listenerClass.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            (proxy, method, args) -> {
              // read at invocation because listeners can be registered before send() captures the
              // caller context
              Context context = callerContext != null ? callerContext : Context.current();
              try (Scope ignored = context.makeCurrent()) {
                return method.invoke(listener, args);
              } catch (InvocationTargetException e) {
                throw e.getCause();
              }
            });
  }

  // Added in Jetty 12.0.20.
  public Request onResponseListener(Response.Listener listener) {
    getResponseListeners().addCompleteListener(wrapResponseListener(listener), true);
    return this;
  }

  @Override
  public Request onResponseBegin(Response.BeginListener listener) {
    return super.onResponseBegin(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseHeader(Response.HeaderListener listener) {
    return super.onResponseHeader(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseHeaders(Response.HeadersListener listener) {
    return super.onResponseHeaders(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseContent(Response.ContentListener listener) {
    return super.onResponseContent(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseContentAsync(Response.AsyncContentListener listener) {
    return super.onResponseContentAsync(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseContentSource(Response.ContentSourceListener listener) {
    return super.onResponseContentSource(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseSuccess(Response.SuccessListener listener) {
    return super.onResponseSuccess(wrapResponseListener(listener));
  }

  @Override
  public Request onResponseFailure(Response.FailureListener listener) {
    return super.onResponseFailure(wrapResponseListener(listener));
  }

  @Override
  public Request onComplete(Response.CompleteListener listener) {
    return super.onComplete(wrapResponseListener(listener));
  }

  @Override
  public void notifyQueued() {
    try (Scope scope = openScope()) {
      super.notifyQueued();
    }
  }

  @Override
  public void notifyBegin() {
    try (Scope scope = openScope()) {
      super.notifyBegin();
    }
  }

  @Override
  public void notifyHeaders() {
    try (Scope scope = openScope()) {
      super.notifyHeaders();
    }
  }

  @Override
  public void notifyCommit() {
    try (Scope scope = openScope()) {
      super.notifyCommit();
    }
  }

  @Override
  public void notifyContent(ByteBuffer byteBuffer) {
    try (Scope scope = openScope()) {
      super.notifyContent(byteBuffer);
    }
  }

  @Override
  public void notifySuccess() {
    try (Scope scope = openScope()) {
      super.notifySuccess();
    }
  }

  @Override
  public void notifyFailure(Throwable failure) {
    try (Scope scope = openScope()) {
      super.notifyFailure(failure);
    }
  }
}
