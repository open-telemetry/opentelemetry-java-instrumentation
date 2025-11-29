/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpSpanNameExtractor;
import io.opentelemetry.instrumentation.servlet.internal.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.internal.ServletHttpAttributesGetter;
import io.opentelemetry.instrumentation.servlet.internal.ServletInstrumenterBuilder;
import io.opentelemetry.instrumentation.servlet.internal.ServletRequestContext;
import io.opentelemetry.instrumentation.servlet.internal.ServletResponseContext;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import java.util.ArrayList;
import java.util.List;

public final class AgentServletInstrumenterBuilder<REQUEST, RESPONSE> {

  private static final List<String> CAPTURE_REQUEST_PARAMETERS =
      AgentInstrumentationConfig.get()
          .getList(
              "otel.instrumentation.servlet.experimental.capture-request-parameters", emptyList());
  private static final boolean CAPTURE_EXPERIMENTAL_ATTRIBUTES =
      AgentInstrumentationConfig.get()
          .getBoolean("otel.instrumentation.servlet.experimental-span-attributes", false);

  private AgentServletInstrumenterBuilder() {}

  private final List<ContextCustomizer<? super ServletRequestContext<REQUEST>>> contextCustomizers =
      new ArrayList<>();

  private boolean propagateOperationListenersToOnEnd;

  public static <REQUEST, RESPONSE> AgentServletInstrumenterBuilder<REQUEST, RESPONSE> create() {
    return new AgentServletInstrumenterBuilder<>();
  }

  @CanIgnoreReturnValue
  public AgentServletInstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer) {
    contextCustomizers.add(contextCustomizer);
    return this;
  }

  @CanIgnoreReturnValue
  public AgentServletInstrumenterBuilder<REQUEST, RESPONSE> propagateOperationListenersToOnEnd() {
    propagateOperationListenersToOnEnd = true;
    return this;
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName,
      ServletAccessor<REQUEST, RESPONSE> accessor,
      SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor,
      HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          httpAttributesGetter) {
    ServletInstrumenterBuilder<REQUEST, RESPONSE> builder =
        ServletInstrumenterBuilder.create(
                instrumentationName, GlobalOpenTelemetry.get(), httpAttributesGetter, accessor)
            .captureRequestParameters(CAPTURE_REQUEST_PARAMETERS)
            .setCaptureExperimentalAttributes(CAPTURE_EXPERIMENTAL_ATTRIBUTES)
            .setCaptureEnduserId(AgentCommonConfig.get().getEnduserConfig().isIdEnabled());
    for (ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer :
        contextCustomizers) {
      builder.addContextCustomizer(contextCustomizer);
    }
    if (propagateOperationListenersToOnEnd) {
      builder.propagateOperationListenersToOnEnd();
    }
    builder.getBuilder().configure(AgentCommonConfig.get());

    return builder.build(spanNameExtractor);
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      String instrumentationName, ServletAccessor<REQUEST, RESPONSE> accessor) {
    HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
        httpAttributesGetter = new ServletHttpAttributesGetter<>(accessor);
    SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor =
        HttpSpanNameExtractor.builder(httpAttributesGetter)
            .setKnownMethods(AgentCommonConfig.get().getKnownHttpRequestMethods())
            .build();

    return build(instrumentationName, accessor, spanNameExtractor, httpAttributesGetter);
  }
}
