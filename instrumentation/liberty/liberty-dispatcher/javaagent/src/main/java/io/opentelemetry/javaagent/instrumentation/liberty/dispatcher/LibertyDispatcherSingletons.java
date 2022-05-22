/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;

public final class LibertyDispatcherSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-dispatcher";

  private static final Instrumenter<LibertyRequest, LibertyResponse> INSTRUMENTER;

  static {
    LibertyDispatcherHttpAttributesGetter httpAttributesGetter =
        new LibertyDispatcherHttpAttributesGetter();
    LibertyDispatcherNetAttributesGetter netAttributesGetter =
        new LibertyDispatcherNetAttributesGetter();

    INSTRUMENTER =
        Instrumenter.<LibertyRequest, LibertyResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpServerAttributesExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
            .addContextCustomizer(HttpRouteHolder.get())
            .addOperationMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(LibertyDispatcherRequestGetter.INSTANCE);
  }

  public static Instrumenter<LibertyRequest, LibertyResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private LibertyDispatcherSingletons() {}
}
