/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

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
public final class MessagingProcessInstrumenterFactory {

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapPropagator propagator,
      TextMapGetter<REQUEST> getter,
      boolean receiveInstrumentationEnabled) {
    if (emitStableMessagingSemconv() || receiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(new PropagatorBasedSpanLinksExtractor<>(propagator, getter));
      if (emitStableMessagingSemconv()) {
        builder.addContextCustomizer(MessagingProcessContextCustomizer.create(propagator, getter));
      }
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    }
    return builder.buildConsumerInstrumenter(getter);
  }

  private MessagingProcessInstrumenterFactory() {}
}
