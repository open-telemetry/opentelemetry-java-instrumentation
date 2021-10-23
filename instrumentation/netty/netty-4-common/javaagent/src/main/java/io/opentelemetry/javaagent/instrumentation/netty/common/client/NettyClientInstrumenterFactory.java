/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;

public final class NettyClientInstrumenterFactory {

  private final String instrumentationName;

  public NettyClientInstrumenterFactory(String instrumentationName) {
    this.instrumentationName = instrumentationName;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesExtractor httpClientAttributesExtractor =
        new NettyHttpClientAttributesExtractor();
    NettyNetClientAttributesExtractor netClientAttributesExtractor =
        new NettyNetClientAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesExtractor))
        .addAttributesExtractor(httpClientAttributesExtractor)
        .addAttributesExtractor(netClientAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netClientAttributesExtractor))
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(new HttpRequestHeadersSetter());
  }
}
