/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v8_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class Jetty8Singletons {

  private static final SpanNameExtractor<HttpServletRequest> spanNameExtractor =
      new Jetty8SpanNameExtractor();
  private static final HttpAttributesExtractor<HttpServletRequest, HttpServletResponse>
      attributesExtractor = new Jetty8AttributesExtactor();
  private static final SpanStatusExtractor<HttpServletRequest, HttpServletResponse>
      spanStatusExtractor = HttpSpanStatusExtractor.create(attributesExtractor);
  private static final NetAttributesExtractor<HttpServletRequest, HttpServletResponse>
      netAttributesExtractor = new Jetty8NetAttributesExtractor();
  private static final TextMapGetter<HttpServletRequest> textMapGetter = new Jetty8Getter();

  private static final Instrumenter<HttpServletRequest, HttpServletResponse> INSTRUMENTER =
      Instrumenter.<HttpServletRequest, HttpServletResponse>newBuilder(
              GlobalOpenTelemetry.get(), "io.opentelemetry.jetty-8.0", spanNameExtractor)
          .setSpanStatusExtractor(spanStatusExtractor)
          .addAttributesExtractor(attributesExtractor)
          .addAttributesExtractor(netAttributesExtractor)
          .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
          .addRequestMetrics(HttpClientMetrics.get())
          .newServerInstrumenter(textMapGetter);

  public static Instrumenter<HttpServletRequest, HttpServletResponse> instrumenter() {
    return INSTRUMENTER;
  }
}
