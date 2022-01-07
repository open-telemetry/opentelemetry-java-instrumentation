/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v2_0;

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
import org.asynchttpclient.Response;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-2.0";

  private static final Instrumenter<RequestContext, Response> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<RequestContext, Response> httpAttributesExtractor =
        new AsyncHttpClientHttpAttributesExtractor();
    SpanNameExtractor<RequestContext> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<RequestContext, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    AsyncHttpClientNetAttributesAdapter netAttributeAdapter = new AsyncHttpClientNetAttributesAdapter();
    NetClientAttributesExtractor<RequestContext, Response> netAttributesExtractor =
        NetClientAttributesExtractor.create(netAttributeAdapter);

    INSTRUMENTER =
        Instrumenter.<RequestContext, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributeAdapter))
            .addAttributesExtractor(new AsyncHttpClientAdditionalAttributesExtractor())
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<RequestContext, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
