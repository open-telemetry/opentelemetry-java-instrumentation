/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpResponse;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-5.0";

  private static final Instrumenter<ClassicHttpRequest, HttpResponse> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<ClassicHttpRequest, HttpResponse> httpAttributesExtractor =
        new ApacheHttpClientHttpAttributesExtractor();
    SpanNameExtractor<? super ClassicHttpRequest> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super ClassicHttpRequest, ? super HttpResponse> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ApacheHttpClientNetAttributesAdapter netAttributesExtractor =
        new ApacheHttpClientNetAttributesAdapter();

    INSTRUMENTER =
        Instrumenter.<ClassicHttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesExtractor))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ClassicHttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientSingletons() {}
}
