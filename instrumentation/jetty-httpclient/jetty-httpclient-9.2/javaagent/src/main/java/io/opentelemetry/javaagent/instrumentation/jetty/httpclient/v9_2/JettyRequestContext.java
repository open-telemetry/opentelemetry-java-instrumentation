/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.JettyHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public final class JettyRequestContext {

  private static final VirtualField<Request, JettyRequestContext> REQUEST_CONTEXT =
      VirtualField.find(Request.class, JettyRequestContext.class);
  private static final VirtualField<Request, RequestListener> REQUEST_LISTENER =
      VirtualField.find(Request.class, RequestListener.class);
  private static final VirtualField<Response, Boolean> PROTOCOL_RESPONSE =
      VirtualField.find(Response.class, Boolean.class);
  private static final RequestListener requestListener = new RequestListener();

  final JettyContentContext caller;
  @Nullable private final Context clientContext;
  private final boolean continuation;
  private final AtomicBoolean ended = new AtomicBoolean();
  private volatile boolean deferredCompletion;

  @Nullable
  public static JettyRequestContext start(HttpRequest request, Context parentContext) {
    if (REQUEST_CONTEXT.get(request) != null) {
      return null;
    }
    Context clientContext =
        instrumenter().shouldStart(parentContext, request)
            ? instrumenter().start(parentContext, request)
            : null;
    JettyRequestContext context =
        new JettyRequestContext(new JettyContentContext(parentContext), clientContext, false);
    REQUEST_CONTEXT.set(request, context);
    registerListener(request);
    return context;
  }

  private JettyRequestContext(
      JettyContentContext caller, @Nullable Context clientContext, boolean continuation) {
    this.caller = caller;
    this.clientContext = clientContext;
    this.continuation = continuation;
  }

  public static void copy(Request original, Request copy) {
    JettyRequestContext context = REQUEST_CONTEXT.get(original);
    if (context != null) {
      // Redirects and authentication retries share the conversation and propagation headers.
      // Only the original request owns span completion.
      REQUEST_CONTEXT.set(copy, new JettyRequestContext(context.caller, null, true));
      registerListener(copy);
      if (context.continuation || context.deferredCompletion) {
        context.complete(original);
      }
    }
  }

  public static void completeContinuation(Request request) {
    JettyRequestContext context = REQUEST_CONTEXT.get(request);
    if (context != null && context.continuation) {
      context.complete(request);
    }
  }

  public static void updateProtocolResponse(
      HttpConversation conversation, @Nullable Response.ResponseListener listener) {
    HttpExchange exchange = conversation.getExchanges().peekLast();
    if (exchange != null) {
      PROTOCOL_RESPONSE.set(exchange.getResponse(), listener != null);
    }
  }

  public static boolean isProtocolResponse(@Nullable Response response) {
    return response != null && Boolean.TRUE.equals(PROTOCOL_RESPONSE.get(response));
  }

  private static void registerListener(Request request) {
    if (REQUEST_LISTENER.get(request) == null) {
      request
          .onRequestFailure(requestListener)
          .onResponseFailure(requestListener)
          .onResponseSuccess(requestListener);
      REQUEST_LISTENER.set(request, requestListener);
    }
  }

  @Nullable
  public static JettyContentContext getCaller(Request request) {
    JettyRequestContext context = REQUEST_CONTEXT.get(request);
    return context == null ? null : context.caller;
  }

  public void end(Request request, @Nullable Response response, @Nullable Throwable failure) {
    if (clientContext != null && ended.compareAndSet(false, true)) {
      instrumenter().end(clientContext, request, response, failure);
    }
  }

  public void complete(Request request) {
    if (REQUEST_CONTEXT.get(request) == this) {
      REQUEST_CONTEXT.set(request, null);
    }
  }

  public void deferCompletion() {
    // A reused request can complete before Jetty's protocol handler copies its next request.
    deferredCompletion = true;
  }

  private static final class RequestListener
      implements Request.FailureListener, Response.SuccessListener, Response.FailureListener {
    @Override
    public void onSuccess(Response response) {
      Request request = response.getRequest();
      JettyRequestContext context = REQUEST_CONTEXT.get(request);
      if (context != null) {
        context.end(request, response, null);
      }
    }

    @Override
    public void onFailure(Request request, Throwable failure) {
      JettyRequestContext context = REQUEST_CONTEXT.get(request);
      if (context != null) {
        context.end(request, null, failure);
      }
    }

    @Override
    public void onFailure(Response response, Throwable failure) {
      Request request = response.getRequest();
      JettyRequestContext context = REQUEST_CONTEXT.get(request);
      if (context != null) {
        context.end(request, response, failure);
      }
    }
  }
}
