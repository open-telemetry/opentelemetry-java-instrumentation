/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import static io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor.alwaysClient;

import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class OkHttp2Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.okhttp-2.2";

  private static final Instrumenter<Request, Response> INSTRUMENTER;
  private static final TracingInterceptor TRACING_INTERCEPTOR;

  static {
    HttpClientAttributesExtractor<Request, Response> httpAttributesExtractor =
        new OkHttp2HttpAttributesExtractor();
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    OkHttp2NetAttributesAdapter netClientAttributesAdapter = new OkHttp2NetAttributesAdapter();
    NetClientAttributesExtractor<Request, Response> netAttributesExtractor =
        NetClientAttributesExtractor.create(netClientAttributesAdapter);

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    INSTRUMENTER =
        Instrumenter.<Request, Response>builder(
                openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(
                PeerServiceAttributesExtractor.create(netClientAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newInstrumenter(alwaysClient());

    TRACING_INTERCEPTOR = new TracingInterceptor(INSTRUMENTER, openTelemetry.getPropagators());
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  public static Interceptor tracingInterceptor() {
    return TRACING_INTERCEPTOR;
  }

  private OkHttp2Singletons() {}
}
