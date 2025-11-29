/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.servlet.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.builder.internal.DefaultHttpServerInstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ServletInstrumenterBuilder<REQUEST, RESPONSE> {

  private final List<ContextCustomizer<? super ServletRequestContext<REQUEST>>> contextCustomizers =
      new ArrayList<>();

  private boolean propagateOperationListenersToOnEnd;
  private boolean captureExperimentalAttributes;
  private boolean captureEnduserId;
  private final List<String> captureRequestParameters = new ArrayList<>();

  private final DefaultHttpServerInstrumenterBuilder<
          ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
      builder;
  private final ServletAccessor<REQUEST, RESPONSE> accessor;

  private ServletInstrumenterBuilder(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          httpAttributesGetter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    this.accessor = accessor;
    builder =
        DefaultHttpServerInstrumenterBuilder.create(
            instrumentationName,
            openTelemetry,
            httpAttributesGetter,
            new ServletRequestGetter<>(accessor));
  }

  public static <REQUEST, RESPONSE> ServletInstrumenterBuilder<REQUEST, RESPONSE> create(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      HttpServerAttributesGetter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
          httpAttributesGetter,
      ServletAccessor<REQUEST, RESPONSE> accessor) {
    return new ServletInstrumenterBuilder<>(
        instrumentationName, openTelemetry, httpAttributesGetter, accessor);
  }

  public DefaultHttpServerInstrumenterBuilder<
          ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
      getBuilder() {
    return builder;
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> addContextCustomizer(
      ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer) {
    contextCustomizers.add(contextCustomizer);
    return this;
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> setCaptureExperimentalAttributes(
      boolean captureExperimentalAttributes) {
    this.captureExperimentalAttributes = captureExperimentalAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> setCaptureEnduserId(
      boolean captureEnduserId) {
    this.captureEnduserId = captureEnduserId;
    return this;
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> propagateOperationListenersToOnEnd() {
    propagateOperationListenersToOnEnd = true;
    return this;
  }

  @CanIgnoreReturnValue
  public ServletInstrumenterBuilder<REQUEST, RESPONSE> captureRequestParameters(
      List<String> captureRequestParameters) {
    this.captureRequestParameters.addAll(captureRequestParameters);
    return this;
  }

  public Instrumenter<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>> build(
      SpanNameExtractor<ServletRequestContext<REQUEST>> spanNameExtractor) {

    builder.setSpanNameExtractor(e -> spanNameExtractor);

    builder.setBuilderCustomizer(
        builder -> {
          if (!captureRequestParameters.isEmpty()) {
            AttributesExtractor<ServletRequestContext<REQUEST>, ServletResponseContext<RESPONSE>>
                requestParametersExtractor =
                    new ServletRequestParametersExtractor<>(accessor, captureRequestParameters);
            builder.addAttributesExtractor(requestParametersExtractor);
          }
          for (ContextCustomizer<? super ServletRequestContext<REQUEST>> contextCustomizer :
              contextCustomizers) {
            builder.addContextCustomizer(contextCustomizer);
          }

          if (propagateOperationListenersToOnEnd) {
            InstrumenterUtil.propagateOperationListenersToOnEnd(builder);
          }

          builder
              .addAttributesExtractor(
                  new ServletAdditionalAttributesExtractor<>(
                      accessor, captureExperimentalAttributes, captureEnduserId))
              .setErrorCauseExtractor(new ServletErrorCauseExtractor<>(accessor));
        });

    return builder.build();
  }
}
