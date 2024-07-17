/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletErrorCauseExtractor;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class TomcatInstrumenterFactory {

  private TomcatInstrumenterFactory() {}

  public static <REQUEST, RESPONSE> Instrumenter<Request, Response> create(
      String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    TomcatHttpAttributesGetter httpAttributesGetter = new TomcatHttpAttributesGetter();

    InstrumenterBuilder<Request, Response> builder =
        Instrumenter.<Request, Response>builder(
                GlobalOpenTelemetry.get(),
                instrumentationName,
                HttpSpanNameExtractor.builder(httpAttributesGetter)
                    .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor))
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
    InstrumenterUtil.propagateOperationListenersToOnEnd(builder);
    return builder.buildServerInstrumenter(TomcatRequestGetter.INSTANCE);
  }
}
