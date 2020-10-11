/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable implements Iterable<ConsumerRecord> {
  private final Iterable<ConsumerRecord> delegate;
  private final KafkaDecorator decorator;
  private boolean firstIterator = true;

  public TracingIterable(Iterable<ConsumerRecord> delegate, KafkaDecorator decorator) {
    this.delegate = delegate;
    this.decorator = decorator;
  }

  @Override
  public Iterator<ConsumerRecord> iterator() {
    Iterator<ConsumerRecord> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // ConsumerRecords is performed in the same thread that called poll()
    if (firstIterator) {
      it = new TracingIterator(delegate.iterator(), decorator);
      firstIterator = false;
    } else {
      it = delegate.iterator();
    }

    return it;
  }
}
