/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;

final class KafkaBatchProcessSpanLinksExtractor implements SpanLinksExtractor<KafkaBatchRequest> {

  private final SpanLinksExtractor<KafkaConsumerRequest> singleRecordLinkExtractor;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, KafkaConsumerRecordGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, KafkaBatchRequest request) {

    for (ConsumerRecord<?, ?> record : request.getConsumerRecords()) {
      // explicitly passing root to avoid situation where context propagation is turned off and the
      // parent (CONSUMER receive) span is linked
      KafkaConsumerRequest consumerRequest = new KafkaConsumerRequest(record, null, null);
      singleRecordLinkExtractor.extract(spanLinks, Context.root(), consumerRequest);
    }
  }
}
