/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.common;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
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
    TomcatHttpAttributesGetter httpAttributesGetter = new TomcatHttpAttributesGetter();
    TomcatNetAttributesGetter netAttributesGetter = new TomcatNetAttributesGetter();
    AttributesExtractor<Request, Response> additionalAttributeExtractor =
        new TomcatAdditionalAttributesExtractor<>(accessor, servletEntityProvider);

    return Instrumenter.<Request, Response>builder(
            GlobalOpenTelemetry.get(),
            instrumentationName,
            HttpSpanNameExtractor.create(httpAttributesGetter))
        .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
        .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor))
        .addAttributesExtractor(HttpServerAttributesExtractor.create(httpAttributesGetter))
        .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
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
