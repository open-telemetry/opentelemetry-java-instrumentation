/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaBatchProcessSpanLinksExtractor implements SpanLinksExtractor<KafkaReceiveRequest> {

  private final TextMapPropagator propagator;
  private final KafkaConsumerRecordGetter recordGetter;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
    this.recordGetter = new KafkaConsumerRecordGetter();
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, KafkaReceiveRequest request) {

    KafkaBatchRecordAttributes attributes = request.getBatchRecordAttributes();
    for (ConsumerRecord<?, ?> record : request.getRecordList()) {
      KafkaProcessRequest processRequest =
          KafkaProcessRequest.create(
              record, request.getConsumerGroup(), request.getClientId(), request.getClusterId());
      Context extracted = propagator.extract(Context.root(), processRequest, recordGetter);
      spanLinks.addLink(
          Span.fromContext(extracted).getSpanContext(), attributes.getLinkAttributes(record));
    }
  }
}
