/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaBatchProcessSpanLinksExtractor implements SpanLinksExtractor<KafkaReceiveRequest> {

  private final SpanLinksExtractor<KafkaProcessRequest> singleRecordLinkExtractor;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, KafkaConsumerRecordGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, KafkaReceiveRequest request) {

    for (ConsumerRecord<?, ?> record : request.getRecords()) {
      singleRecordLinkExtractor.extract(
          spanLinks,
          parentContext,
          KafkaProcessRequest.create(record, request.getConsumerGroup(), request.getClientId()));
    }
  }
}
