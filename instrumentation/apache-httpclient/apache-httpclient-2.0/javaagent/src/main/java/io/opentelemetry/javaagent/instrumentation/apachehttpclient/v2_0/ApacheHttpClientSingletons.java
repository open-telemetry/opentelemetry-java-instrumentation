/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

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
import org.apache.commons.httpclient.HttpMethod;

public final class ApacheHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-httpclient-2.0";

  private static final Instrumenter<HttpMethod, HttpMethod> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<HttpMethod, HttpMethod> httpAttributesExtractor =
        new ApacheHttpClientHttpAttributesExtractor();
    SpanNameExtractor<? super HttpMethod> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super HttpMethod, ? super HttpMethod> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ApacheHttpClientNetAttributesAdapter netAttributesAdapter =
        new ApacheHttpClientNetAttributesAdapter();
    NetClientAttributesExtractor<HttpMethod, HttpMethod> netAttributesExtractor = new NetClientAttributesExtractor<>(netAttributesAdapter);
    INSTRUMENTER =
        Instrumenter.<HttpMethod, HttpMethod>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<HttpMethod, HttpMethod> instrumenter() {
    return INSTRUMENTER;
  }

  private ApacheHttpClientSingletons() {}
}
