/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;

public final class ServletInstrumenterBuilder {

  private ServletInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE>
      InstrumenterBuilder<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          newBuilder(
              String instrumentationName,
              ServletAccessor<REQUEST, RESPONSE> accessor,
              SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor,
              HttpAttributesExtractor<
                      ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
                  httpAttributesExtractor) {
    SpanStatusExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        spanStatusExtractor = HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ServletNetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor =
        new ServletNetAttributesExtractor<>(accessor);
    ServletErrorCauseExtractor<REQUEST, RESPONSE> errorCauseExtractor =
        new ServletErrorCauseExtractor<>(accessor);
    AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        additionalAttributesExtractor = new ServletAdditionalAttributesExtractor<>(accessor);

    return Instrumenter
        .<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>newBuilder(
            GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
        .setSpanStatusExtractor(spanStatusExtractor)
        .setErrorCauseExtractor(errorCauseExtractor)
        .addAttributesExtractor(httpAttributesExtractor)
        .addAttributesExtractor(netAttributesExtractor)
        .addAttributesExtractor(PeerServiceAttributesExtractor.create(netAttributesExtractor))
        .addAttributesExtractor(additionalAttributesExtractor)
        .addRequestMetrics(HttpServerMetrics.get());
  }

  public static <REQUEST, RESPONSE>
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          newInstrumenter(
              String instrumentationName,
              ServletAccessor<REQUEST, RESPONSE> accessor,
              TextMapGetter<ServletRequestContext<REQUEST>> requestGetter) {
    HttpAttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        httpAttributesExtractor = new ServletHttpAttributesExtractor<>(accessor);
    // TODO: if we were filling http.route we could use
    // HttpSpanNameExtractor.create(httpAttributesExtractor);
    SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor =
        new ServletSpanNameExtractor<>(accessor);

    return newBuilder(instrumentationName, accessor, spanNameExtractor, httpAttributesExtractor)
        .newServerInstrumenter(requestGetter);
  }
}
