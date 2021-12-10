/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.servlet.ServerSpanNaming;
import java.util.Collections;
import java.util.List;
import org.restlet.Request;
import org.restlet.Response;

public class RestletInstrumenterFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.restlet-2.0";

  public static Instrumenter<Request, Response> newServerInstrumenter(OpenTelemetry openTelemetry) {
    return newServerInstrumenter(
        openTelemetry, CapturedHttpHeaders.server(Config.get()), Collections.emptyList());
  }

  public static Instrumenter<Request, Response> newServerInstrumenter(
      OpenTelemetry openTelemetry,
      CapturedHttpHeaders capturedHttpHeaders,
      List<AttributesExtractor<Request, Response>> additionalExtractors) {

    HttpServerAttributesExtractor<Request, Response> httpAttributesExtractor =
        new RestletHttpAttributesExtractor(capturedHttpHeaders);
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetServerAttributesExtractor<Request, Response> netAttributesExtractor =
        new RestletNetAttributesExtractor();

    return Instrumenter.<Request, Response>builder(
            openTelemetry, INSTRUMENTATION_NAME, spanNameExtractor)
        .setSpanStatusExtractor(spanStatusExtractor)
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractors(additionalExtractors)
        .addRequestMetrics(HttpServerMetrics.get())
        .addContextCustomizer(ServerSpanNaming.get())
        .newServerInstrumenter(new RestletHeadersGetter());
  }
}
