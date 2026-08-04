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
public class MessagingProcessInstrumenterFactory {

  public static <REQUEST, RESPONSE> Instrumenter<REQUEST, RESPONSE> create(
      InstrumenterBuilder<REQUEST, RESPONSE> builder,
      TextMapPropagator propagator,
      TextMapGetter<REQUEST> getter,
      boolean receiveInstrumentationEnabled) {
    if (emitStableMessagingSemconv()) {
      // both the span links extractor below and the context customizer added after it extract the
      // message creation context, so the propagator runs twice per process span. Instrumenter runs
      // span links extractors before context customizers and gives them no shared state, so the
      // result can't simply be handed over. Caching it (e.g. in InstrumenterContext) would trade
      // the second extraction for a thread local lookup, a map operation and a capturing lambda,
      // which is not obviously cheaper than extracting a single header again, so the extraction is
      // deliberately repeated instead.
      //
      // the creation context is linked even when it ends up being this span's parent, which happens
      // when there is no ambient span; semconv asks for a link to the creation context for every
      // message the span accounts for
      builder.addSpanLinksExtractor(new PropagatorBasedSpanLinksExtractor<>(propagator, getter));
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
