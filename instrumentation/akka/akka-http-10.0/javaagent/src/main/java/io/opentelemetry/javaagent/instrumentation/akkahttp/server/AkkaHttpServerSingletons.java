/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.stream.ActorAttributes;
import akka.stream.Attributes;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;

public class AkkaHttpServerSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  public static final String OTEL_DISPATCHER_NAME = "otel-pinned-dispatcher";
  public static final Attributes OTEL_DISPATCHER_ATTRIBUTES =
    ActorAttributes.dispatcher(OTEL_DISPATCHER_NAME).and(Attributes.name("otel-pinned-actor"));

  static {
    AkkaHttpServerAttributesGetter httpAttributesGetter = new AkkaHttpServerAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<HttpRequest, HttpResponse>builder(
                GlobalOpenTelemetry.get(),
                AkkaHttpUtil.instrumentationName(),
                HttpSpanNameExtractor.create(httpAttributesGetter))
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .addAttributesExtractor(HttpServerAttributesExtractor.create(httpAttributesGetter))
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(HttpRouteHolder.get())
            .newServerInstrumenter(AkkaHttpServerHeaders.INSTANCE);
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }
}
