/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

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
import org.apache.http.HttpResponse;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-4.0";

  private static final Instrumenter<ApacheHttpClientRequest, HttpResponse> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<ApacheHttpClientRequest, HttpResponse> httpAttributesExtractor =
        new ApacheHttpClientHttpAttributesExtractor();
    SpanNameExtractor<? super ApacheHttpClientRequest> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super ApacheHttpClientRequest, ? super HttpResponse> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ApacheHttpClientNetAttributesAdapter netAttributesExtractor =
        new ApacheHttpClientNetAttributesAdapter();

    INSTRUMENTER =
        Instrumenter.<ApacheHttpClientRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(new NetClientAttributesExtractor<>(netAttributesExtractor))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientSingletons() {}
}
