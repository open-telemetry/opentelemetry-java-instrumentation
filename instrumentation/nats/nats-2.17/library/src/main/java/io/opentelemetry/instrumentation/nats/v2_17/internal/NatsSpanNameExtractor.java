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
  private static final String LEGACY_SETTLEMENT_SPAN_NAME = "$JS.ACK publish";

  private final SpanNameExtractor<NatsRequest> delegate;
  private final MessagingOperationType operationType;

  static SpanNameExtractor<NatsRequest> create(
      MessagingAttributesGetter<NatsRequest, ?> getter,
      MessagingOperationType operationType,
      String operationName) {
    return new NatsSpanNameExtractor(
        MessagingSpanNameExtractor.create(getter, operationType, operationName), operationType);
  }

  private NatsSpanNameExtractor(
      SpanNameExtractor<NatsRequest> delegate, MessagingOperationType operationType) {
    this.delegate = delegate;
    this.operationType = operationType;
  }

  @Override
  public String extract(NatsRequest request) {
    if (!emitStableMessagingSemconv() && NatsSubject.isJetStreamSettlement(request.getSubject())) {
      return LEGACY_SETTLEMENT_SPAN_NAME;
    }
    if (operationType == MessagingOperationType.SETTLE
        && NatsSubject.isJetStreamSettlement(request.getSubject())) {
      String operationName = request.getJetStreamSettlementOperationName();
      return (operationName == null ? "settle" : operationName)
          + " "
          + NatsSubject.JETSTREAM_ACK_SUBJECT;
    }
    return delegate.extract(request);
  }
}
