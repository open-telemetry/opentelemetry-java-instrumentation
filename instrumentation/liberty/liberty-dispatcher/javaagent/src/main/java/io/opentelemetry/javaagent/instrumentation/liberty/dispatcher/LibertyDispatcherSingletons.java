/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import static io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming.Source.CONTAINER;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;

public final class LibertyDispatcherSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-dispatcher";

  private static final Instrumenter<LibertyRequest, LibertyResponse> INSTRUMENTER;

  static {
    HttpServerAttributesExtractor<LibertyRequest, LibertyResponse> httpAttributesExtractor =
        new LibertyDispatcherHttpAttributesExtractor();
    SpanNameExtractor<LibertyRequest> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<LibertyRequest, LibertyResponse> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetServerAttributesExtractor<LibertyRequest, LibertyResponse> netAttributesExtractor =
        new LibertyDispatcherNetAttributesExtractor();

    INSTRUMENTER =
        Instrumenter.<LibertyRequest, LibertyResponse>builder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addContextCustomizer(
                (context, request, attributes) -> ServerSpanNaming.init(context, CONTAINER))
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(LibertyDispatcherRequestGetter.INSTANCE);
  }

  public static Instrumenter<LibertyRequest, LibertyResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private LibertyDispatcherSingletons() {}
}
