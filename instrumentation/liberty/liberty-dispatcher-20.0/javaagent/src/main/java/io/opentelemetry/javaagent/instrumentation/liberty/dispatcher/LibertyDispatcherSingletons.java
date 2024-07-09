/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.liberty.dispatcher;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;

public final class LibertyDispatcherSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.liberty-dispatcher-20.0";

  private static final Instrumenter<LibertyRequest, LibertyResponse> INSTRUMENTER;

  static {
    LibertyDispatcherHttpAttributesGetter httpAttributesGetter =
        new LibertyDispatcherHttpAttributesGetter();

    InstrumenterBuilder<LibertyRequest, LibertyResponse> builder =
        Instrumenter.<LibertyRequest, LibertyResponse>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(AgentCommonConfig.get().getServerRequestHeaders())
                    .setCapturedResponseHeaders(AgentCommonConfig.get().getServerResponseHeaders())
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addContextCustomizer(
                HttpServerRoute.builder(httpAttributesGetter)
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addOperationMetrics(HttpServerMetrics.get());
    if (AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildServerInstrumenter(LibertyDispatcherRequestGetter.INSTANCE);
  }

  public static Instrumenter<LibertyRequest, LibertyResponse> instrumenter() {
    return INSTRUMENTER;
  }

  private LibertyDispatcherSingletons() {}
}
