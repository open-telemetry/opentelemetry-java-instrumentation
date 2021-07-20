/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.execchain.ClientExecChain;

final class TracingHttpClientBuilder extends HttpClientBuilder {

  private final Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter;
  private final ContextPropagators propagators;

  TracingHttpClientBuilder(
      Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter,
      ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  protected ClientExecChain decorateProtocolExec(ClientExecChain protocolExec) {
    return new TracingProtocolExec(instrumenter, propagators, protocolExec);
  }
}
