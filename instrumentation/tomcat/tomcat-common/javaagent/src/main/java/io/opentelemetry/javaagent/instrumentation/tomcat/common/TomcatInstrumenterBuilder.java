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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.servlet.ServletErrorCauseExtractor;
import org.apache.coyote.Request;
import org.apache.coyote.Response;

public final class TomcatInstrumenterBuilder {

  private TomcatInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE> Instrumenter<Request, Response> newInstrumenter(
      String instrumentationName,
      ServletAccessor<REQUEST, RESPONSE> accessor,
      TomcatServletEntityProvider<REQUEST, RESPONSE> servletEntityProvider) {
    HttpAttributesExtractor<Request, Response> httpAttributesExtractor =
        new TomcatHttpAttributesExtractor();
    SpanNameExtractor<Request> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanStatusExtractor<Request, Response> spanStatusExtractor =
        HttpSpanStatusExtractor.create(httpAttributesExtractor);
    NetAttributesExtractor<Request, Response> netAttributesExtractor =
        new TomcatNetAttributesExtractor();
    AttributesExtractor<Request, Response> additionalAttributeExtractor =
        new TomcatAdditionalAttributesExtractor<>(accessor, servletEntityProvider);

    return Instrumenter.<Request, Response>newBuilder(
            GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
        .setSpanStatusExtractor(spanStatusExtractor)
        .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor))
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
        .addAttributesExtractor(additionalAttributeExtractor)
        .addRequestMetrics(HttpServerMetrics.get())
        .newServerInstrumenter(TomcatRequestGetter.GETTER);
  }
}
