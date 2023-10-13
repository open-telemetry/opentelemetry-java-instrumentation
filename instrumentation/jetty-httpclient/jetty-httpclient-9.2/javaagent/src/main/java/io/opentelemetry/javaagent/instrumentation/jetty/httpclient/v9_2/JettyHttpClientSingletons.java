/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientHttpAttributesGetter;
import io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal.JettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

public class JettyHttpClientSingletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      JettyClientInstrumenterFactory.create(
          GlobalOpenTelemetry.get(),
          builder ->
              builder
                  .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                  .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                  .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          singletonList(
              HttpClientPeerServiceAttributesExtractor.create(
                  JettyClientHttpAttributesGetter.INSTANCE,
                  CommonConfig.get().getPeerServiceResolver())),
          CommonConfig.get().shouldEmitExperimentalHttpClientMetrics());

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private JettyHttpClientSingletons() {}
}
