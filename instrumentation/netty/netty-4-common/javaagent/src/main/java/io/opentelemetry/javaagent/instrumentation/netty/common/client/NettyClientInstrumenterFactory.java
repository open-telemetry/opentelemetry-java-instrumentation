/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyCommonNetAttributesExtractor;

public final class NettyClientInstrumenterFactory {

  private final String instrumentationName;

  public NettyClientInstrumenterFactory(String instrumentationName) {
    this.instrumentationName = instrumentationName;
  }

  public Instrumenter<HttpRequestAndChannel, HttpResponse> createHttpInstrumenter() {
    NettyHttpClientAttributesExtractor httpClientAttributesExtractor =
        new NettyHttpClientAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>newBuilder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.createClient(httpClientAttributesExtractor))
        .addAttributesExtractor(httpClientAttributesExtractor)
        .addAttributesExtractor(new NettyCommonNetAttributesExtractor())
        // TODO: add peer extractor attributes once Net*AttributesExtractors are refactored
        // .addAttributesExtractor(PeerServiceAttributesExtractor.create(netClientAttributesExtractor))
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(new HttpRequestHeadersSetter());
  }
}
