/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

final class KafkaBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<ConsumerRecords<?, ?>> {

  private final SpanLinksExtractor<ConsumerRecord<?, ?>> singleRecordLinkExtractor;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        SpanLinksExtractor.extractFromRequest(propagator, KafkaConsumerRecordGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, ConsumerRecords<?, ?> records) {

    for (ConsumerRecord<?, ?> record : records) {
      // explicitly passing root to avoid situation where context propagation is turned off and the
      // parent (CONSUMER receive) span is linked
      singleRecordLinkExtractor.extract(spanLinks, Context.root(), record);
    }
  }
}
