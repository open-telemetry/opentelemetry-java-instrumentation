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
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpSpanStatusExtractor;
import io.opentelemetry.instrumentation.api.servlet.MappingResolver;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

public final class ServletInstrumenterBuilder<REQUEST, RESPONSE> {

  private ServletInstrumenterBuilder() {}

  @Nullable
  private Function<ServletRequestContext<REQUEST>, MappingResolver> mappingResolverFunction;

  private final List<ContextCustomizer<? super ServletRequestContext<REQUEST>>> contextCustomizers =
      new ArrayList<>();

  public static <REQUEST, RESPONSE> ServletInstrumenterBuilder<REQUEST, RESPONSE> create() {
    return new ServletInstrumenterBuilder<>();
  }

  public ServletInstrumenterBuilder<REQUEST, RESPONSE> setMappingResolverFunction(
      Function<ServletRequestContext<REQUEST>, MappingResolver> mappingResolverFunction) {
    this.mappingResolverFunction = mappingResolverFunction;
    return this;
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
    ServletNetAttributesExtractor<REQUEST, RESPONSE> netAttributesExtractor =
        new ServletNetAttributesExtractor<>(accessor);
    ServletErrorCauseExtractor<REQUEST, RESPONSE> errorCauseExtractor =
        new ServletErrorCauseExtractor<>(accessor);
    AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        additionalAttributesExtractor = new ServletAdditionalAttributesExtractor<>(accessor);

    InstrumenterBuilder<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> builder =
        Instrumenter.<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>newBuilder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .setSpanStatusExtractor(spanStatusExtractor)
            .setErrorCauseExtractor(errorCauseExtractor)
            .addAttributesExtractor(httpAttributesExtractor)
            .addAttributesExtractor(netAttributesExtractor)
            .addAttributesExtractor(additionalAttributesExtractor)
            .addRequestMetrics(HttpServerMetrics.get());
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
        new ServletSpanNameExtractor<>(accessor, mappingResolverFunction);

    return build(instrumentationName, accessor, spanNameExtractor, httpAttributesExtractor);
  }
}
