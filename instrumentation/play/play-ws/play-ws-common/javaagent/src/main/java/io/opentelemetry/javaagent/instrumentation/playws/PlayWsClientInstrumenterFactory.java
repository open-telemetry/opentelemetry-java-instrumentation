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

public final class PlayWsClientInstrumenterFactory {

  public static Instrumenter<Request, Response> createInstrumenter(String instrumentationName) {
    PlayWsClientHttpAttributesGetter httpAttributesGetter = new PlayWsClientHttpAttributesGetter();
    PlayWsClientNetAttributesGetter netAttributesGetter = new PlayWsClientNetAttributesGetter();

    return Instrumenter.<Request, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(HttpClientAttributesExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(NetClientAttributesExtractor.create(netAttributesGetter))
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesGetter))
        .addOperationMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(HttpHeaderSetter.INSTANCE);
  }

  private PlayWsClientInstrumenterFactory() {}
}
