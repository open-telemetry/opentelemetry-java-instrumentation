/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientResend;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import javax.annotation.Nullable;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class InstrumentationContexts {

  private volatile Context parentContext;
  private volatile Timer timer;
  // on retries, reactor-netty starts the next resend attempt before it ends the previous one (i.e.
  // it calls the callback functions in that order); thus for a short moment there can be multiple
  // coexisting HTTP client spans
  private final Queue<RequestAndContext> clientContexts = new LinkedBlockingQueue<>();

  void initialize(Context parentContext) {
    this.parentContext = HttpClientResend.initialize(parentContext);
    timer = Timer.start();
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
      clientContexts.offer(new RequestAndContext(request, context));
    }
    return context;
  }

  void endClientSpan(@Nullable HttpClientResponse response, @Nullable Throwable error) {
    RequestAndContext requestAndContext = clientContexts.poll();
    if (requestAndContext != null) {
      instrumenter().end(requestAndContext.context, requestAndContext.request, response, error);
    }
  }

  void startAndEndConnectionErrorSpan(HttpClientRequest request, Throwable error) {
    Context parentContext = this.parentContext;
    if (instrumenter().shouldStart(parentContext, request)) {
      Timer timer = this.timer;
      InstrumenterUtil.startAndEnd(
          instrumenter(), parentContext, request, null, error, timer.startTime(), timer.now());
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
