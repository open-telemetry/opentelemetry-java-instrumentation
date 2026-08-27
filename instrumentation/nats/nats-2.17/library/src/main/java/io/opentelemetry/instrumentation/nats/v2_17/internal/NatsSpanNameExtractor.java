/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_17.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

final class NatsSpanNameExtractor implements SpanNameExtractor<NatsRequest> {
  private static final String LEGACY_SETTLEMENT_SPAN_NAME = "$JS.ACK settle";

  private final SpanNameExtractor<NatsRequest> delegate;

  static SpanNameExtractor<NatsRequest> create(
      MessagingAttributesGetter<NatsRequest, ?> getter, String operationName) {
    return new NatsSpanNameExtractor(
        MessagingSpanNameExtractor.create(getter, MessagingOperationType.SETTLE, operationName));
  }

  private NatsSpanNameExtractor(SpanNameExtractor<NatsRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public String extract(NatsRequest request) {
    if (NatsSubject.isJetStreamSettlement(request.getSubject())) {
      if (!emitStableMessagingSemconv()) {
        return LEGACY_SETTLEMENT_SPAN_NAME;
      }
      String operationName = request.getJetStreamSettlementOperationName();
      return (operationName == null ? "settle" : operationName)
          + " "
          + NatsSubject.JETSTREAM_ACK_SUBJECT;
    }
    return delegate.extract(request);
  }
}
