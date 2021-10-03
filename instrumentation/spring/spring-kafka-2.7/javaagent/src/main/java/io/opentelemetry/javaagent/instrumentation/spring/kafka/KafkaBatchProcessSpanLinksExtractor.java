/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerIteratorWrapper;
import io.opentelemetry.instrumentation.kafka.KafkaConsumerRecordGetter;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class KafkaBatchProcessSpanLinksExtractor
    implements SpanLinksExtractor<ConsumerRecords<?, ?>> {

  private final SpanLinksExtractor<ConsumerRecord<?, ?>> singleRecordLinkExtractor;

  public KafkaBatchProcessSpanLinksExtractor(ContextPropagators contextPropagators) {
    this.singleRecordLinkExtractor =
        SpanLinksExtractor.fromUpstreamRequest(contextPropagators, new KafkaConsumerRecordGetter());
  }

  @Override
  public void extract(
      SpanLinksBuilder spanLinks, Context parentContext, ConsumerRecords<?, ?> records) {

    Iterator<? extends ConsumerRecord<?, ?>> it = records.iterator();

    // this will forcefully suppress the kafka-clients CONSUMER instrumentation even though there's
    // no current CONSUMER span
    if (it instanceof KafkaConsumerIteratorWrapper) {
      it = ((KafkaConsumerIteratorWrapper<?, ?>) it).unwrap();
    }

    while (it.hasNext()) {
      ConsumerRecord<?, ?> record = it.next();
      // explicitly passing root to avoid situation where context propagation is turned off and the
      // parent (CONSUMER receive) span is linked
      singleRecordLinkExtractor.extract(spanLinks, Context.root(), record);
    }
  }
}
