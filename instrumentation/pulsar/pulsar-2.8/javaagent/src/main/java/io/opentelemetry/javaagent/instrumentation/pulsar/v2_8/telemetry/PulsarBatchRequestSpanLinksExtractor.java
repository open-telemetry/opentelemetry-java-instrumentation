/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.pulsar.client.api.Message;

final class PulsarBatchRequestSpanLinksExtractor implements SpanLinksExtractor<PulsarBatchRequest> {
  private final SpanLinksExtractor<PulsarRequest> singleRecordLinkExtractor;

  PulsarBatchRequestSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, MessageTextMapGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, PulsarBatchRequest request) {

    for (Message<?> message : request.getMessages()) {
      singleRecordLinkExtractor.extract(
          spanLinks, Context.root(), PulsarRequest.create(message, request.getUrlData()));
    }
  }
}
