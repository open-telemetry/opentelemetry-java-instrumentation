/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaBatchProcessSpanLinksExtractor implements SpanLinksExtractor<KafkaReceiveRequest> {

  private final SpanLinksExtractor<KafkaProcessRequest> singleRecordLinkExtractor;
  private final TextMapPropagator propagator;
  private final KafkaConsumerRecordGetter recordGetter;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.propagator = propagator;
    this.recordGetter = new KafkaConsumerRecordGetter();
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, recordGetter);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, KafkaReceiveRequest request) {

    if (!emitStableMessagingSemconv()) {
      for (ConsumerRecord<?, ?> record : request.getRecords()) {
        singleRecordLinkExtractor.extract(
            spanLinks,
            parentContext,
            KafkaProcessRequest.create(
                record, request.getConsumerGroup(), request.getClientId(), request.getClusterId()));
      }
      return;
    }

    KafkaBatchRecordAttributes attributes = request.getBatchRecordAttributes();
    for (ConsumerRecord<?, ?> record : request.getRecords()) {
      KafkaProcessRequest processRequest =
          KafkaProcessRequest.create(
              record, request.getConsumerGroup(), request.getClientId(), request.getClusterId());
      Context extracted = propagator.extract(Context.root(), processRequest, recordGetter);
      spanLinks.addLink(
          Span.fromContext(extracted).getSpanContext(), attributes.getLinkAttributes(record));
    }
  }
}
