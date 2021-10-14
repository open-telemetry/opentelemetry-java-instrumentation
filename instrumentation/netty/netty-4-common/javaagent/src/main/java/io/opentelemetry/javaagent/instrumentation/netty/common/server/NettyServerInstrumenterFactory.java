/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.server;

import io.netty.handler.codec.http.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.HttpRequestAndChannel;

public final class NettyServerInstrumenterFactory {

  public static Instrumenter<HttpRequestAndChannel, HttpResponse> create(
      String instrumentationName) {

    final NettyHttpServerAttributesExtractor httpAttributesExtractor =
        new NettyHttpServerAttributesExtractor();

    return Instrumenter.<HttpRequestAndChannel, HttpResponse>newBuilder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpAttributesExtractor))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesExtractor))
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(new NettyNetServerAttributesExtractor())
        .addRequestMetrics(HttpServerMetrics.get())
        .newServerInstrumenter(new HttpRequestHeadersGetter());
  }

  private NettyServerInstrumenterFactory() {}
}
