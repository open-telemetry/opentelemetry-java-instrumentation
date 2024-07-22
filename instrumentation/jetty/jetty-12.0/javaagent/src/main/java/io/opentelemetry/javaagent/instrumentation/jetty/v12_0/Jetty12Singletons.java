/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.v12_0;

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
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

public final class Jetty12Singletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jetty-12.0";

  private static final Instrumenter<Request, Response> INSTRUMENTER;

  static {
    Jetty12HttpAttributesGetter httpAttributesGetter = new Jetty12HttpAttributesGetter();

    InstrumenterBuilder<Request, Response> builder =
        Instrumenter.<Request, Response>builder(
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
            .addContextCustomizer(
                (context, request, attributes) ->
                    new AppServerBridge.Builder()
                        .captureServletAttributes()
                        .recordException()
                        .init(context))
            .addOperationMetrics(HttpServerMetrics.get());
    if (AgentCommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildServerInstrumenter(Jetty12TextMapGetter.INSTANCE);
  }

  private static final Jetty12Helper HELPER = new Jetty12Helper(INSTRUMENTER);

  public static Jetty12Helper helper() {
    return HELPER;
  }

  private Jetty12Singletons() {}
}
