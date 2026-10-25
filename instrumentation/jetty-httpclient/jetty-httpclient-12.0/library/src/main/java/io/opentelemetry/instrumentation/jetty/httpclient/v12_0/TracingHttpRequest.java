/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientTracingListener;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientWrapUtil;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;
import org.eclipse.jetty.http.HttpFields;

class TracingHttpRequest extends HttpRequest {

  private static final String LIBRARY_CONTEXT_BOUNDARY_ATTRIBUTE =
      "otel-jetty-httpclient-library-context-boundary";

  private final Instrumenter<Request, Response> instrumenter;
  private Context parentContext = Context.root();
  private boolean callerContextCaptured;
  @Nullable private Context context;

  TracingHttpRequest(
      HttpClient client,
      HttpConversation conversation,
      URI uri,
      Instrumenter<Request, Response> instrumenter) {
    super(client, conversation, uri);
    this.instrumenter = instrumenter;
    attribute(LIBRARY_CONTEXT_BOUNDARY_ATTRIBUTE, true);
  }

  @Override
  public Request body(Request.Content content) {
    if (callerContextCaptured && content != null) {
      content = JettyClientWrapUtil.wrapRequestContent(content, parentContext);
    }
    return super.body(content);
  }

  @Override
  public Request trailersSupplier(Supplier<HttpFields> trailersSupplier) {
    if (callerContextCaptured && trailersSupplier != null) {
      trailersSupplier = JettyClientWrapUtil.wrapRequestTrailers(trailersSupplier, parentContext);
    }
    return super.trailersSupplier(trailersSupplier);
  }

  @Override
  public void send(Response.CompleteListener listener) {
    parentContext = Context.current();
    callerContextCaptured = true;
    Request.Content body = getBody();
    if (body != null) {
      super.body(JettyClientWrapUtil.wrapRequestContent(body, parentContext));
    }
    Supplier<HttpFields> trailersSupplier = getTrailersSupplier();
    if (trailersSupplier != null) {
      super.trailersSupplier(
          JettyClientWrapUtil.wrapRequestTrailers(trailersSupplier, parentContext));
    }
    // start span and attach listeners - must handle all listeners, not just CompleteListener.
    Context startedContext =
        JettyClientTracingListener.handleRequest(parentContext, this, instrumenter);
    context = startedContext == null ? parentContext : startedContext;
    try (Scope ignored = openScope()) {
      super.send(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
    }
  }

  @Nullable
  private Scope openScope() {
    return context != null ? context.makeCurrent() : null;
  }

  private Scope openParentScope() {
    return parentContext.makeCurrent();
  }

  // Added in Jetty 12.0.20.
  public Request onResponseListener(Response.Listener listener) {
    getResponseListeners()
        .addCompleteListener(JettyClientWrapUtil.wrapListener(listener, () -> parentContext), true);
    return this;
  }

  @Override
  public Request onResponseBegin(Response.BeginListener listener) {
    return super.onResponseBegin(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseHeader(Response.HeaderListener listener) {
    return super.onResponseHeader(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseHeaders(Response.HeadersListener listener) {
    return super.onResponseHeaders(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseContent(Response.ContentListener listener) {
    return super.onResponseContent(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseContentAsync(Response.AsyncContentListener listener) {
    return super.onResponseContentAsync(
        JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseContentSource(Response.ContentSourceListener listener) {
    return super.onResponseContentSource(
        JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseSuccess(Response.SuccessListener listener) {
    return super.onResponseSuccess(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onResponseFailure(Response.FailureListener listener) {
    return super.onResponseFailure(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public Request onComplete(Response.CompleteListener listener) {
    return super.onComplete(JettyClientWrapUtil.wrapListener(listener, () -> parentContext));
  }

  @Override
  public void notifyQueued() {
    try (Scope scope = openParentScope()) {
      super.notifyQueued();
    }
  }

  @Override
  public void notifyBegin() {
    try (Scope scope = openParentScope()) {
      super.notifyBegin();
    }
  }

  @Override
  public void notifyHeaders() {
    try (Scope scope = openParentScope()) {
      super.notifyHeaders();
    }
  }

  @Override
  public void notifyCommit() {
    try (Scope scope = openParentScope()) {
      super.notifyCommit();
    }
  }

  @Override
  public void notifyContent(ByteBuffer byteBuffer) {
    try (Scope scope = openParentScope()) {
      super.notifyContent(byteBuffer);
    }
  }

  @Override
  public void notifySuccess() {
    try (Scope scope = openParentScope()) {
      super.notifySuccess();
    }
  }

  @Override
  public void notifyFailure(Throwable failure) {
    try (Scope scope = openParentScope()) {
      super.notifyFailure(failure);
    }
  }
}
