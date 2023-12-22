/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpExperimentalAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.http.HttpServerExperimentalMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerMetrics;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerRoute;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanStatusExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import java.util.ArrayList;
import java.util.List;

public final class ServletInstrumenterBuilder<REQUEST, RESPONSE> {

  private ServletInstrumenterBuilder() {}

  private final List<ContextCustomizer<? super ServletRequestContext<REQUEST>>> contextCustomizers =
      new ArrayList<>();

  public static <REQUEST, RESPONSE> ServletInstrumenterBuilder<REQUEST, RESPONSE> create() {
    return new ServletInstrumenterBuilder<>();
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer) {
    contextCustomizers.add(contextCustomizer);
    return this;
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName,
      ServletAccessor<REQUEST, RESPONSE> accessor,
      SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor,
      HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          httpAttributesGetter) {

    ServletErrorCauseExtractor<REQUEST, RESPONSE> errorCauseExtractor =
        new ServletErrorCauseExtractor<>(accessor);
    AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        additionalAttributesExtractor = new ServletAdditionalAttributesExtractor<>(accessor);

    InstrumenterBuilder<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> builder =
        Instrumenter.<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>builder(
                GlobalOpenTelemetry.get(), instrumentationName, spanNameExtractor)
            .setSpanStatusExtractor(HttpSpanStatusExtractor.create(httpAttributesGetter))
            .setErrorCauseExtractor(errorCauseExtractor)
            .addAttributesExtractor(
                HttpServerAttributesExtractor.builder(httpAttributesGetter)
                    .setCapturedRequestHeaders(CommonConfig.get().getServerRequestHeaders())
                    .setCapturedResponseHeaders(CommonConfig.get().getServerResponseHeaders())
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build())
            .addAttributesExtractor(additionalAttributesExtractor)
            .addOperationMetrics(HttpServerMetrics.get())
            .addContextCustomizer(
                HttpServerRoute.builder(httpAttributesGetter)
                    .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
                    .build());
    if (ServletRequestParametersExtractor.enabled()) {
      AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          requestParametersExtractor = new ServletRequestParametersExtractor<>(accessor);
      builder.addAttributesExtractor(requestParametersExtractor);
    }
    for (ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer :
        contextCustomizers) {
      builder.addContextCustomizer(contextCustomizer);
    }
    if (CommonConfig.get().shouldEmitExperimentalHttpServerTelemetry()) {
      builder
          .addAttributesExtractor(HttpExperimentalAttributesExtractor.create(httpAttributesGetter))
          .addOperationMetrics(HttpServerExperimentalMetrics.get());
    }
    return builder.buildServerInstrumenter(new ServletRequestGetter<>(accessor));
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        httpAttributesGetter = new ServletHttpAttributesGetter<>(accessor);
    SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor =
        HttpSpanNameExtractor.builder(httpAttributesGetter)
            .setKnownMethods(CommonConfig.get().getKnownHttpRequestMethods())
            .build();

    return build(instrumentationName, accessor, spanNameExtractor, httpAttributesGetter);
  }
}
