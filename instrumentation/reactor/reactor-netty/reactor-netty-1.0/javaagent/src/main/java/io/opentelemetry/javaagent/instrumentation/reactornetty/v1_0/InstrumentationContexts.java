/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0.ReactorNettySingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import javax.annotation.Nullable;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class InstrumentationContexts {
  private static final VirtualField<HttpClientRequest, Context> REQUEST_CONTEXT =
      VirtualField.find(HttpClientRequest.class, Context.class);

  private static final AtomicReferenceFieldUpdater<InstrumentationContexts, Initialization>
      initializationUpdater =
          AtomicReferenceFieldUpdater.newUpdater(
              InstrumentationContexts.class, Initialization.class, "initialization");

  private volatile Initialization initialization;
  // on retries, reactor-netty starts the next resend attempt before it ends the previous one (i.e.
  // it calls the callback functions in that order); thus for a short moment there can be multiple
  // coexisting HTTP client spans
  private final Queue<RequestAndContext> clientContexts = new LinkedBlockingQueue<>();

  void initialize(Context parentContext) {
    Context parentContextWithResends = HttpClientRequestResendCount.initialize(parentContext);
    // make sure initialization happens only once
    initializationUpdater.compareAndSet(
        this, null, new Initialization(parentContextWithResends, Timer.start()));
  }

  Context getParentContext() {
    return initialization.parentContext;
  }

  @Nullable
  Context getClientContext() {
    RequestAndContext requestAndContext = clientContexts.peek();
    return requestAndContext == null ? null : requestAndContext.context;
  }

  @Nullable
  Context startClientSpan(HttpClientRequest request) {
    Context parentContext = initialization.parentContext;
    Context context = null;
    if (instrumenter().shouldStart(parentContext, request)) {
      context = instrumenter().start(parentContext, request);
      REQUEST_CONTEXT.set(request, context);
      clientContexts.offer(new RequestAndContext(request, context));
    }
    return context;
  }

  void endClientSpan(@Nullable HttpClientResponse response, @Nullable Throwable error) {
    HttpClientRequest request = null;
    Context context = null;
    RequestAndContext requestAndContext = clientContexts.poll();
    if (response instanceof HttpClientRequest) {
      request = (HttpClientRequest) response;
      context = REQUEST_CONTEXT.get(request);
    } else if (requestAndContext != null) {
      // this branch is taken when there was an error (e.g. timeout) and response was null
      request = requestAndContext.request;
      context = requestAndContext.context;
    }

    if (request != null && context != null) {
      instrumenter().end(context, request, response, error);
    }
  }

  void startAndEndConnectionErrorSpan(HttpClientRequest request, Throwable error) {
    Initialization initialization = this.initialization;
    Context parentContext = initialization.parentContext;
    if (instrumenter().shouldStart(parentContext, request)) {
      Timer timer = initialization.timer;
      InstrumenterUtil.startAndEnd(
          instrumenter(), parentContext, request, null, error, timer.startTime(), timer.now());
    }
  }

  private static final class Initialization {
    private final Context parentContext;
    private final Timer timer;

    private Initialization(Context parentContext, Timer timer) {
      this.parentContext = parentContext;
      this.timer = timer;
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
