/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import java.util.function.BiFunction;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class MessagingProcessContextCustomizer<REQUEST>
    implements ContextCustomizer<REQUEST> {

  public static <REQUEST> ContextCustomizer<REQUEST> create(
      TextMapPropagator propagator, TextMapGetter<REQUEST> getter) {
    return create((parentContext, request) -> propagator.extract(parentContext, request, getter));
  }

  public static <REQUEST> ContextCustomizer<REQUEST> create(
      BiFunction<Context, REQUEST, Context> producerContextExtractor) {
    return new MessagingProcessContextCustomizer<>(producerContextExtractor);
  }

  private final BiFunction<Context, REQUEST, Context> producerContextExtractor;

  private MessagingProcessContextCustomizer(
      BiFunction<Context, REQUEST, Context> producerContextExtractor) {
    this.producerContextExtractor = producerContextExtractor;
  }

  @Override
  public Context onStart(Context parentContext, REQUEST request, Attributes startAttributes) {
    if (Span.fromContext(parentContext).getSpanContext().isValid()) {
      return parentContext;
    }
    return producerContextExtractor.apply(parentContext, request);
  }
}
