/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v12_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientHttpAttributesGetter;
import io.opentelemetry.instrumentation.jetty.httpclient.v12_0.internal.JettyClientInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import java.util.function.Function;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.Response;

public class JettyHttpClientSingletons {

  static final String JETTY_CLIENT_CONTEXT_KEY = "otel-jetty-client-context";

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      JettyClientInstrumenterFactory.create(
          GlobalOpenTelemetry.get(),
          builder ->
              builder
                  .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                  .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                  .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
          Function.identity(),
          singletonList(
              HttpClientPeerServiceAttributesExtractor.create(
                  JettyClientHttpAttributesGetter.INSTANCE,
                  CommonConfig.get().getPeerServiceResolver())),
          CommonConfig.get().shouldEmitExperimentalHttpClientTelemetry());

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private JettyHttpClientSingletons() {}
}
