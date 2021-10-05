/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesServerExtractor;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class JdkHttpClientSingletons {

  private static final HttpHeadersInjectAdapter SETTER;
  private static final Instrumenter<HttpRequest, HttpResponse<?>> INSTRUMENTER;

  static {
    SETTER = new HttpHeadersInjectAdapter(GlobalOpenTelemetry.getPropagators());
    JdkHttpAttributesExtractor httpAttributesExtractor = new JdkHttpAttributesExtractor();
    SpanNameExtractor<HttpRequest> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<HttpRequest, HttpResponse<?>> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    JdkHttpNetAttributesExtractor netAttributesExtractor = new JdkHttpNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<HttpRequest, HttpResponse<?>>newBuilder(
                GlobalOpenTelemetry.get(), "io.opentelemetry.java-http-client", spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesServerExtractor.create(netAttributesExtractor))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(SETTER);
  }

  public static Instrumenter<HttpRequest, HttpResponse<?>> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpHeadersInjectAdapter setter() {
    return SETTER;
  }

  private JdkHttpClientSingletons() {}
}
