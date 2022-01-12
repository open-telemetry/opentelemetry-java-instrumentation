/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;

public class AkkaHttpClientSingletons {

  private static final HttpHeaderSetter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    SETTER = new HttpHeaderSetter(GlobalOpenTelemetry.getPropagators());
    AkkaHttpClientAttributesExtractor httpAttributesExtractor =
        new AkkaHttpClientAttributesExtractor();
    AkkaHttpNetAttributesGetter netAttributesAdapter = new AkkaHttpNetAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<HttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                AkkaHttpUtil.instrumentationName(),
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesAdapter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeaderSetter setter() {
    return SETTER;
  }

  private AkkaHttpClientSingletons() {}
}
