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
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;

class TracingHttpRequest extends HttpRequest {

  private final Instrumenter<Request, Response> instrumenter;
  private Context parentContext;

  public TracingHttpRequest(
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
    super.send(JettyClientWrapUtil.wrapTheListener(listener, parentContext));
  }

  private Scope openScope() {
    return parentContext != null ? parentContext.makeCurrent() : null;
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
