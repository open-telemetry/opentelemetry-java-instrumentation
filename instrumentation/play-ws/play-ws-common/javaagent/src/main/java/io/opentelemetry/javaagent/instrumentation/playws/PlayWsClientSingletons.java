/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
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
    PlayWsClientHttpAttributesGetter httpAttributesGetter = new PlayWsClientHttpAttributesGetter();
    PlayWsClientNetAttributesGetter netAttributesGetter = new PlayWsClientNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<Request, Response>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
            .addRequestMetrics(HttpClientMetrics.get())
            .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  public static Instrumenter<Request, Response> instrumenter() {
    return INSTRUMENTER;
  }

  private PlayWsClientSingletons() {}
}
