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

class TracingHttpRequest extends HttpRequest {

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
          if (parentContext != null) {
            try (Scope scope = parentContext.makeCurrent()) {
              listener.onComplete(result);
            }
          } else {
            listener.onComplete(result);
          }
        });
  }

  @Override
  public void notifyQueued() {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyQueued();
      }
    } else {
      super.notifyQueued();
    }
  }

  @Override
  public void notifyBegin() {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyBegin();
      }
    } else {
      super.notifyBegin();
    }
  }

  @Override
  public void notifyHeaders() {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyHeaders();
      }
    } else {
      super.notifyHeaders();
    }
  }

  @Override
  public void notifyCommit() {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyCommit();
      }
    } else {
      super.notifyCommit();
    }
  }

  @Override
  public void notifyContent(ByteBuffer byteBuffer) {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyContent(byteBuffer);
      }
    } else {
      super.notifyContent(byteBuffer);
    }
  }

  @Override
  public void notifySuccess() {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifySuccess();
      }
    } else {
      super.notifySuccess();
    }
  }

  @Override
  public void notifyFailure(Throwable failure) {
    if (parentContext != null) {
      try (Scope scope = parentContext.makeCurrent()) {
        super.notifyFailure(failure);
      }
    } else {
      super.notifyFailure(failure);
    }
  }
}
