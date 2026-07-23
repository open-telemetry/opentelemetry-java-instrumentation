/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class MessagingProcessInstrumenterFactory {

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapPropagator propagator,
      TextMapGetter<REQUEST> getter,
      boolean receiveInstrumentationEnabled) {
    if (emitStableMessagingSemconv()) {
      builder.addSpanLinksExtractor(
          (spanLinks, parentContext, request) -> {
            SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
            SpanContext producerSpanContext =
                Span.fromContext(propagator.extract(parentContext, request, getter))
                    .getSpanContext();
            if (parentSpanContext.isValid()
                && producerSpanContext.isValid()
                && (!producerSpanContext.getTraceId().equals(parentSpanContext.getTraceId())
                    || !producerSpanContext.getSpanId().equals(parentSpanContext.getSpanId()))) {
              spanLinks.addLink(producerSpanContext);
            }
          });
      builder.addContextCustomizer(MessagingProcessContextCustomizer.create(propagator, getter));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }
    if (receiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(new PropagatorBasedSpanLinksExtractor<>(propagator, getter));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }
    return builder.buildConsumerInstrumenter(getter);
  }

  private MessagingProcessInstrumenterFactory() {}
}
