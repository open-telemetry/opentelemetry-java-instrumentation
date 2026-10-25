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
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;

public final class JettyRequestContext
    implements Request.FailureListener,
        Response.SuccessListener,
        Response.FailureListener,
        Response.CompleteListener {

  private static final VirtualField<Request, JettyRequestContext> REQUEST_CONTEXT =
      VirtualField.find(Request.class, JettyRequestContext.class);

  final JettyContentContext caller;
  @Nullable private final Context clientContext;
  private final AtomicBoolean ended = new AtomicBoolean();

  @Nullable
  public static JettyRequestContext start(HttpRequest request, Context parentContext) {
    if (REQUEST_CONTEXT.get(request) != null) {
      return null;
    }
    Context clientContext =
        instrumenter().shouldStart(parentContext, request)
            ? instrumenter().start(parentContext, request)
            : null;
    JettyRequestContext context = new JettyRequestContext(parentContext, clientContext);
    REQUEST_CONTEXT.set(request, context);
    request
        .onRequestFailure(context)
        .onResponseFailure(context)
        .onResponseSuccess(context)
        .onComplete(context);
    return context;
  }

  private JettyRequestContext(Context caller, @Nullable Context clientContext) {
    this.caller = new JettyContentContext(caller);
    this.clientContext = clientContext;
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

  @Override
  public void onSuccess(Response response) {
    end(response.getRequest(), response, null);
  }

  @Override
  public void onFailure(Request request, Throwable failure) {
    end(request, null, failure);
  }

  @Override
  public void onFailure(Response response, Throwable failure) {
    end(response.getRequest(), response, failure);
  }

  @Override
  public void onComplete(Result result) {
    complete(result.getRequest());
  }
}
