/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v12_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyHttpClient12TracingInterceptor;
import java.net.URI;
import java.nio.ByteBuffer;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;
import org.eclipse.jetty.client.transport.HttpConversation;
import org.eclipse.jetty.client.transport.HttpRequest;

public class TracingHttpRequest extends HttpRequest {

  private Context parentContext;

  private final Instrumenter<Request, Response> instrumenter;

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
    JettyHttpClient12TracingInterceptor interceptor =
        new JettyHttpClient12TracingInterceptor(parentContext, instrumenter);
    // start span
    interceptor.attachToRequest(this);
    super.send(
        result -> {
          Scope scope = null;
          if (parentContext != null) {
            scope = parentContext.makeCurrent();
          }
          // async call
          listener.onComplete(result);
          if (scope != null) {
            scope.close();
          }
        });
  }

  @Override
  public void notifyQueued() {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyQueued();
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifyBegin() {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyBegin();
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifyHeaders() {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyHeaders();
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifyCommit() {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyCommit();
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifyContent(ByteBuffer byteBuffer) {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyContent(byteBuffer);
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifySuccess() {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifySuccess();
    if (scope != null) {
      scope.close();
    }
  }

  @Override
  public void notifyFailure(Throwable failure) {
    Scope scope = null;
    if (parentContext != null) {
      scope = parentContext.makeCurrent();
    }
    super.notifyFailure(failure);
    if (scope != null) {
      scope.close();
    }
  }
}
