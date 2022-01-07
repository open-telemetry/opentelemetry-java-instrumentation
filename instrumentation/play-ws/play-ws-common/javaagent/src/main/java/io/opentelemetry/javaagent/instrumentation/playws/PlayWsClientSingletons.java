/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

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
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWsClientSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.play-ws-common";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    HttpClientAttributesExtractor<Request, Response> httpAttributesExtractor =
        new PlayWsClientHttpAttributesExtractor();
    SpanNameExtractor<? super Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<? super Request, ? super Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    PlayWsClientNetAttributesExtractor netAttributesAdapter =
        new PlayWsClientNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<Request, Response>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(new NetClientAttributesExtractor<>(netAttributesAdapter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesAdapter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private PlayWsClientSingletons() {}
}
