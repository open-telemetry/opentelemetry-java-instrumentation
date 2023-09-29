/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static java.util.Collections.singletonList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientPeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.httpclient.internal.HttpHeadersSetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientAttributesGetter;
import io.opentelemetry.instrumentation.httpclient.internal.JavaHttpClientInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JavaHttpClientSingletons {

  private static final HttpHeadersSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersSetter(GlobalOpenTelemetry.getPropagators());

    INSTRUMENTER =
        JavaHttpClientInstrumenterFactory.createInstrumenter(
            GlobalOpenTelemetry.get(),
            builder ->
                builder
                    .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
            builder -> builder.setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods()),
            singletonList(
                HttpClientPeerServiceAttributesExtractor.create(
                    JavaHttpClientAttributesGetter.INSTANCE,
                    CommonConfig.get().getPeerServiceResolver())),
            CommonConfig.get().shouldEmitExperimentalHttpClientMetrics());
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersSetter setter() {
    return SETTER;
  }

  private JavaHttpClientSingletons() {}
}
