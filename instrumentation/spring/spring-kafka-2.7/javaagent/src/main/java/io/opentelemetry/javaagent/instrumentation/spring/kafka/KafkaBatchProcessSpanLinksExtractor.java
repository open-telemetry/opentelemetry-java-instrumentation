/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerRecordGetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

final class KafkaBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<ConsumerRecords<?, ?>> {

  private final SpanLinksExtractor<ConsumerRecord<?, ?>> singleRecordLinkExtractor;

  KafkaBatchProcessSpanLinksExtractor(ContextPropagators contextPropagators) {
    this.singleRecordLinkExtractor =
        SpanLinksExtractor.fromUpstreamRequest(
            contextPropagators, KafkaConsumerRecordGetter.INSTANCE);
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
