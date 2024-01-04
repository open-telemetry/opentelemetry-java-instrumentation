/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

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
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;

public final class PekkoHttpServerSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    PekkoHttpServerAttributesGetter httpAttributesGetter = new PekkoHttpServerAttributesGetter();
    InstrumenterBuilder<HttpRequest, HttpResponse> builder =
        Instrumenter.<HttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                PekkoHttpUtil.instrumentationName(),
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(
                HttpServerRoute.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build());
    if (CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    INSTRUMENTER = builder.buildServerInstrumenter(PekkoHttpServerHeaders.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }

  private PekkoHttpServerSingletons() {}
}
