/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.server.ServerSpanNaming;
import java.util.ArrayList;
import java.util.List;

public final class ServletInstrumenterBuilder<REQUEST, RESPONSE> {

  private ServletInstrumenterBuilder() {}

  private final List<ContextCustomizer<? super ServletRequestContext<REQUEST>>> contextCustomizers =
      new ArrayList<>();

  public static <REQUEST, RESPONSE> ServletInstrumenterBuilder<REQUEST, RESPONSE> create() {
    return new ServletInstrumenterBuilder<>();
  }

  public ServletInstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer) {
    contextCustomizers.add(contextCustomizer);
    return this;
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName,
      ServletAccessor<REQUEST, RESPONSE> accessor,
      SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor,
      HttpServerAttributesExtractor<
              ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          httpAttributesExtractor) {

    SpanStatusExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        spanStatusExtractor = HttpSpanStatusExtractor.create(httpAttributesExtractor);
    ServletNetAttributesGetter<REQUEST, RESPONSE> netAttributesGetter =
        new ServletNetAttributesGetter<>(accessor);
    ServletErrorCauseExtractor<REQUEST, RESPONSE> errorCauseExtractor =
        new ServletErrorCauseExtractor<>(accessor);
    AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        additionalAttributesExtractor = new ServletAdditionalAttributesExtractor<>(accessor);

    InstrumenterBuilder<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> builder =
        Instrumenter.<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .setErrorCauseExtractor(errorCauseExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(NetServerAttributesExtractor.create(netAttributesGetter))
            .addAttributesExtractor(additionalAttributesExtractor)
            .addRequestMetrics(HttpServerMetrics.get())
            .addContextCustomizer(ServerSpanNaming.get());
    if (ServletRequestParametersExtractor.enabled()) {
      AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          requestParametersExtractor = new ServletRequestParametersExtractor<>(accessor);
      builder.addAttributesExtractor(requestParametersExtractor);
    }
    for (ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer :
        contextCustomizers) {
      builder.addContextCustomizer(contextCustomizer);
    }
    return builder.newServerInstrumenter(new ServletRequestGetter<>(accessor));
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    HttpServerAttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        httpAttributesExtractor = new ServletHttpAttributesExtractor<>(accessor);
    SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor =
        HttpSpanNameExtractor.create(httpAttributesExtractor);

    return build(instrumentationName, accessor, spanNameExtractor, httpAttributesExtractor);
  }
}
