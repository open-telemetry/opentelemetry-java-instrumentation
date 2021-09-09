/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable<K, V> implements Iterable<ConsumerRecord<K, V>> {
  private final Iterable<ConsumerRecord<K, V>> delegate;
  private boolean firstIterator = true;

  public TracingIterable(Iterable<ConsumerRecord<K, V>> delegate) {
    this.delegate = delegate;
  }

  @Override
  public Iterator<ConsumerRecord<K, V>> iterator() {
    Iterator<ConsumerRecord<K, V>> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // ConsumerRecords is performed in the same thread that called poll()
    if (firstIterator) {
      it = new TracingIterator<>(delegate.iterator());
      firstIterator = false;
    } else {
      it = delegate.iterator();
    }

    return it;
  }
}
