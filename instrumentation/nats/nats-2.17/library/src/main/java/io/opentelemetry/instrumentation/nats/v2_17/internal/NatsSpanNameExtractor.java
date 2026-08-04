/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class NatsSpanNameExtractor implements SpanNameExtractor<NatsRequest> {
  private static final String JETSTREAM_ACK_SPAN_NAME = "nats.ack";

  private final SpanNameExtractor<NatsRequest> delegate;

  static SpanNameExtractor<NatsRequest> create(
      MessagingAttributesGetter<NatsRequest, ?> getter, MessageOperation operation) {
    return new NatsSpanNameExtractor(MessagingSpanNameExtractor.create(getter, operation));
  }

  private NatsSpanNameExtractor(SpanNameExtractor<NatsRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public String extract(NatsRequest request) {
    if (NatsSubject.isJetStreamAck(request.getSubject())) {
      return JETSTREAM_ACK_SPAN_NAME;
    }
    return delegate.extract(request);
  }
}
