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
import org.apache.kafka.clients.consumer.ConsumerRecords;

final class KafkaBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<ConsumerAndRecord<ConsumerRecords<?, ?>>> {

  private final SpanLinksExtractor<ConsumerAndRecord<ConsumerRecord<?, ?>>>
      singleRecordLinkExtractor;

  KafkaBatchProcessSpanLinksExtractor(TextMapPropagator propagator) {
    this.singleRecordLinkExtractor =
        new PropagatorBasedSpanLinksExtractor<>(propagator, KafkaConsumerRecordGetter.INSTANCE);
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks,
      Context parentContext,
      ConsumerAndRecord<ConsumerRecords<?, ?>> consumerAndRecords) {

    for (ConsumerRecord<?, ?> record : consumerAndRecords.record()) {
      // explicitly passing root to avoid situation where context propagation is turned off and the
      // parent (CONSUMER receive) span is linked
      singleRecordLinkExtractor.extract(
          spanLinks,
          Context.root(),
          ConsumerAndRecord.create(consumerAndRecords.consumer(), record));
    }
  }
}
