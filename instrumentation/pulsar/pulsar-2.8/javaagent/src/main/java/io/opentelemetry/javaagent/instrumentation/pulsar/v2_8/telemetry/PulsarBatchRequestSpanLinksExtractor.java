/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import org.apache.pulsar.client.api.Message;

final class PulsarBatchRequestSpanLinksExtractor implements SpanLinksExtractor<PulsarBatchRequest> {

  private final TextMapPropagator propagator;

  PulsarBatchRequestSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, PulsarBatchRequest request) {

    PulsarBatchRecordAttributes batchRecordAttributes = request.getBatchRecordAttributes();
    for (Message<?> message : request.getMessages()) {
      PulsarRequest messageRequest =
          PulsarRequest.create(message, request.getUrlData(), request.getSubscription());
      Context extracted =
          propagator.extract(Context.root(), messageRequest, MessageTextMapGetter.INSTANCE);
      spanLinks.addLink(
          Span.fromContext(extracted).getSpanContext(),
          batchRecordAttributes.getLinkAttributes(messageRequest));
    }
  }
}
