/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.reactornetty.v1_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import java.util.concurrent.atomic.AtomicReference;
import reactor.netty.http.client.HttpClient;

public class ReactorNettyTelemetry {

  private final InstrumentationContexts contexts;

  private final ContextPropagators propagators;

  ReactorNettyTelemetry(InstrumentationContexts contexts, ContextPropagators propagators) {
    this.contexts = contexts;
    this.propagators = propagators;
  }

  public HttpClient tracingHttpClient(HttpClient httpClient) {
    AtomicReference<Context> clientContext = new AtomicReference<>();
    return httpClient
        .doOnRequest(
            (request, connection) -> {
              // create span
              contexts.startClientSpan(request);
              propagators
                  .getTextMapPropagator()
                  .inject(clientContext.get(), request, HttpClientRequestHeadersSetter.INSTANCE);
            })
        .doOnResponse(
            (response, connection) -> {
              contexts.endClientSpan(response, null);
            })
        .doOnResponseError(contexts::endClientSpan);
  }
}
