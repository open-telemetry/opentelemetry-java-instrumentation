/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;

public class AkkaHttpServerSingletons {

  private static final Instrumenter<HttpRequest, HttpResponse> INSTRUMENTER;

  static {
    AkkaHttpServerAttributesExtractor httpAttributesExtractor =
        new AkkaHttpServerAttributesExtractor();
    INSTRUMENTER =
        Instrumenter.<HttpRequest, HttpResponse>newBuilder(
                GlobalOpenTelemetry.get(),
                AkkaHttpUtil.instrumentationName(),
                unused -> "akka.request")
            .setSpanStatusExtractor(HttpSpanStatusExtractor.createServer(httpAttributesExtractor))
            .addAttributesExtractor(httpAttributesExtractor)
            .addRequestMetrics(HttpServerMetrics.get())
            .newServerInstrumenter(new AkkaHttpServerHeaders());
  }

  public static Instrumenter<HttpRequest, HttpResponse> instrumenter() {
    return INSTRUMENTER;
  }

  public static HttpResponse errorResponse() {
    return (HttpResponse) HttpResponse.create().withStatus(500);
  }
}
