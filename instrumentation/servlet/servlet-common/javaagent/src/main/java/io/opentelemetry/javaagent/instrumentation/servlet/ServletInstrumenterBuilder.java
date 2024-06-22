/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenterBuilder;
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

    DefaultHttpServerInstrumenterBuilder<
            ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        serverBuilder =
            new DefaultHttpServerInstrumenterBuilder<>(
                    instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter)
                .setHeaderGetter(new ServletRequestGetter<>(accessor));
    serverBuilder.setSpanNameExtractor(e -> spanNameExtractor);
    return JavaagentHttpServerInstrumenterBuilder.createWithCustomizer(
        serverBuilder,
        builder -> {
          if (ServletRequestParametersExtractor.enabled()) {
            AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
                requestParametersExtractor = new ServletRequestParametersExtractor<>(accessor);
            builder.addAttributesExtractor(requestParametersExtractor);
          }
          for (ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer :
              contextCustomizers) {
            builder.addContextCustomizer(contextCustomizer);
          }

          builder
              .addAttributesExtractor(new ServletAdditionalAttributesExtractor<>(accessor))
              .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor));
        });
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
