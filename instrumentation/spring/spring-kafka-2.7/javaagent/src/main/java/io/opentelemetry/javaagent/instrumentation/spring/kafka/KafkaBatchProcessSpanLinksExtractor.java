/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

<<<<<<< HEAD
=======
import io.opentelemetry.instrumentation.kafka.KafkaConsumerRecordGetter;
>>>>>>> Move classes used by multiple instrumentations into bootstrap module to ensure that everybody uses the same copy of them
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
    if (it instanceof KafkaClientsConsumerProcessWrapper) {
      it = ((KafkaClientsConsumerProcessWrapper<Iterator<? extends ConsumerRecord<?, ?>>>) it).unwrap();
    }

    while (it.hasNext()) {
      ConsumerRecord<?, ?> record = it.next();
      // explicitly passing root to avoid situation where context propagation is turned off and the
      // parent (CONSUMER receive) span is linked
      singleRecordLinkExtractor.extract(spanLinks, Context.root(), record);
    }
  }
}
