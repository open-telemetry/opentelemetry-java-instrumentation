/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class NatsSpanNameExtractor implements SpanNameExtractor<NatsRequest> {
  private static final String JETSTREAM_SETTLE_SPAN_NAME = "nats.settle";

  private final SpanNameExtractor<NatsRequest> delegate;

  static SpanNameExtractor<NatsRequest> create(
      MessagingAttributesGetter<NatsRequest, ?> getter,
      MessagingOperationType operationType,
      String operationName) {
    return new NatsSpanNameExtractor(
        MessagingSpanNameExtractor.create(getter, operationType, operationName));
  }

  private NatsSpanNameExtractor(SpanNameExtractor<NatsRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public String extract(NatsRequest request) {
    if (NatsSubject.isJetStreamAck(request.getSubject())) {
      return JETSTREAM_SETTLE_SPAN_NAME;
    }
    return delegate.extract(request);
  }
}
