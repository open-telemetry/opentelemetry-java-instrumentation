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
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyCommonNetAttributesExtractor;

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

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>newBuilder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpClientAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpClientAttributesExtractor))
        .addAttributesExtractor(httpClientAttributesExtractor)
        // in case of netty client instrumentation we're using 2 net attributes extractors:
        // 1. the common one will extract net attributes on start of the operation; in case of
        // timeouts or other connection issues netty may return null when calling
        // Channel.remoteAddress() at the end of processing
        // 2. the client one will extract full net attributes at the end of processing - it should
        // be the fully resolved address at this point in time
        .addAttributesExtractor(new NettyCommonNetAttributesExtractor())
        .addAttributesExtractor(netClientAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netClientAttributesExtractor))
        .addRequestMetrics(HttpClientMetrics.get())
        .newClientInstrumenter(new HttpRequestHeadersSetter());
  }
}
