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
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;

class TracingHttpRequest extends HttpRequest {

  private final Instrumenter<Request, Response> instrumenter;
  @Nullable private Context parentContext;

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
    parentContext = Context.current();
    // start span and attach listeners - must handle all listeners, not just CompleteListener.
    JettyClientTracingListener.handleRequest(parentContext, this, instrumenter);
    super.send(JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Nullable
  private Scope openScope() {
    return parentContext != null ? parentContext.makeCurrent() : null;
  }

  private Context responseContext() {
    return parentContext != null ? parentContext : Context.current();
  }

  // Added in Jetty 12.0.20.
  public Request onResponseListener(Response.Listener listener) {
    getResponseListeners()
        .addCompleteListener(
            JettyClientWrapUtil.wrapListener(listener, this::responseContext), true);
    return this;
  }

  @Override
  public Request onResponseBegin(Response.BeginListener listener) {
    return super.onResponseBegin(JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseHeader(Response.HeaderListener listener) {
    return super.onResponseHeader(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseHeaders(Response.HeadersListener listener) {
    return super.onResponseHeaders(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseContent(Response.ContentListener listener) {
    return super.onResponseContent(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseContentAsync(Response.AsyncContentListener listener) {
    return super.onResponseContentAsync(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseContentSource(Response.ContentSourceListener listener) {
    return super.onResponseContentSource(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseSuccess(Response.SuccessListener listener) {
    return super.onResponseSuccess(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onResponseFailure(Response.FailureListener listener) {
    return super.onResponseFailure(
        JettyClientWrapUtil.wrapListener(listener, this::responseContext));
  }

  @Override
  public Request onComplete(Response.CompleteListener listener) {
    return super.onComplete(JettyClientWrapUtil.wrapListener(listener, this::responseContext));
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
