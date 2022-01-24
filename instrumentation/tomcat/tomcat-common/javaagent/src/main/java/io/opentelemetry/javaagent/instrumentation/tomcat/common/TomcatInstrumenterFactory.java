/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.javaagent.bootstrap.servlet.AppServerBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletErrorCauseExtractor;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class TomcatInstrumenterFactory {

  private TomcatInstrumenterFactory() {}

  public static <REQUEST, RESPONSE> Instrumenter<Request, Response> create(
      String instrumentationName,
      ServletAccessor<REQUEST, RESPONSE> accessor,
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider) {
    HttpServerAttributesExtractor<Request, Response> httpAttributesExtractor =
        new TomcatHttpAttributesExtractor();
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetServerAttributesExtractor<Request, Response> netAttributesExtractor =
        NetServerAttributesExtractor.create(new TomcatNetAttributesGetter());
    AttributesExtractor<Request, Response> additionalAttributeExtractor =
        new TomcatAdditionalAttributesExtractor<>(accessor, servletEntityProvider);

    return Instrumenter.<Request, Response>builder(
            GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
        .setSpanStatusExtractor(spanStatusExtractor)
        .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor))
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(additionalAttributeExtractor)
        .addContextCustomizer(HttpRouteHolder.get())
        .addContextCustomizer(
            (context, request, attributes) ->
                new AppServerBridge.Builder()
                    .captureServletAttributes()
                    .recordException()
                    .init(context))
        .addRequestMetrics(HttpServerMetrics.get())
        .newServerInstrumenter(TomcatRequestGetter.INSTANCE);
  }
}
