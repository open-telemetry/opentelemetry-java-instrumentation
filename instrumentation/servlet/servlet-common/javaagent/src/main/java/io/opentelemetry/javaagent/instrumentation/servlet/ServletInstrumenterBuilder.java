/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.javaagent.instrumentation.api.instrumenter.PeerServiceAttributesExtractor;

public final class ServletInstrumenterBuilder {

  private ServletInstrumenterBuilder() {}

  public static <REQUEST, RESPONSE>
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          newInstrumenter(
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
        .addRequestMetrics(HttpServerMetrics.get())
        .newServerInstrumenter(new ServletRequestGetter<>(accessor));
  }

  public static <REQUEST, RESPONSE>
      Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          newInstrumenter(String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    HttpAttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        httpAttributesExtractor = new ServletHttpAttributesExtractor<>(accessor);
    SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor =
        new ServletSpanNameExtractor<>(accessor);

    return newInstrumenter(
        instrumentationName, accessor, spanNameExtractor, httpAttributesExtractor);
  }
}
