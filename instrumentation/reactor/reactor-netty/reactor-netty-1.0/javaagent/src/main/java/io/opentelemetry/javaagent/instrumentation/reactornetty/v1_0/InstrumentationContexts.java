/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientResend;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class InstrumentationContexts {

  static final Logger logger = Logger.getLogger(InstrumentationContexts.class.getName());

  private volatile Context parentContext;
  // on retries, reactor-netty starts the next resend attempt before it ends the previous one (i.e.
  // it calls the callback functions in that order); thus for a short moment there can be 2
  // coexisting HTTP client spans
  private final Queue<RequestAndContext> clientContexts = new ArrayBlockingQueue<>(2, true);

  void initialize(Context parentContext) {
    this.parentContext = HttpClientResend.initialize(parentContext);
  }

  Context getParentContext() {
    return parentContext;
  }

  @Nullable
  Context getClientContext() {
    RequestAndContext requestAndContext = clientContexts.peek();
    return requestAndContext == null ? null : requestAndContext.context;
  }

  @Nullable
  Context startClientSpan(HttpClientRequest request) {
    Context parentContext = this.parentContext;
    Context context = null;
    if (instrumenter().shouldStart(parentContext, request)) {
      context = instrumenter().start(parentContext, request);
      if (!clientContexts.offer(new RequestAndContext(request, context))) {
        // should not ever happen in reality
        String message =
            "Could not instrument HTTP client request; not enough space in the request queue";
        logger.log(Level.FINE, message);
        instrumenter().end(context, request, null, new IllegalStateException(message));
      }
    }
    return context;
  }

  void endClientSpan(@Nullable HttpClientResponse response, @Nullable Throwable error) {
    RequestAndContext requestAndContext = clientContexts.poll();
    if (requestAndContext != null) {
      instrumenter().end(requestAndContext.context, requestAndContext.request, response, error);
    }
  }

  static final class RequestAndContext {
    final HttpClientRequest request;
    final Context context;

    RequestAndContext(HttpClientRequest request, Context context) {
      this.request = request;
      this.context = context;
    }
  }
}
