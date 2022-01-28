/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.v1_9;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;

public final class AsyncHttpClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.async-http-client-1.9";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    AsyncHttpClientHttpAttributesGetter httpAttributesExtractor =
        new AsyncHttpClientHttpAttributesGetter();
    AsyncHttpClientNetAttributesGetter netAttributesGetter =
        new AsyncHttpClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<Request, Response>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesExtractor))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesExtractor))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private AsyncHttpClientSingletons() {}
}
