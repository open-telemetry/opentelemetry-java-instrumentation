/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.BooleanSupplier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TracingListIterator<K, V> implements ListIterator<ConsumerRecord<K, V>> {

  private final ListIterator<ConsumerRecord<K, V>> delegateListIterator;
  private final Iterator<ConsumerRecord<K, V>> tracingIterator;

  private TracingListIterator(
      ListIterator<ConsumerRecord<K, V>> delegateListIterator,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    this.delegateListIterator = delegateListIterator;
    this.tracingIterator =
        TracingIterator.wrap(delegateListIterator, instrumenter, wrappingEnabled, consumerContext);
  }

  public static <K, V> ListIterator<ConsumerRecord<K, V>> wrap(
      ListIterator<ConsumerRecord<K, V>> delegateListIterator,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    if (wrappingEnabled.getAsBoolean()) {
      return new TracingListIterator<>(
          delegateListIterator, instrumenter, wrappingEnabled, consumerContext);
    }
    return delegateListIterator;
  }

  @Override
  public boolean hasNext() {
    return tracingIterator.hasNext();
  }

  @Override
  public ConsumerRecord<K, V> next() {
    return tracingIterator.next();
  }

  @Override
  public boolean hasPrevious() {
    return delegateListIterator.hasPrevious();
  }

  @Override
  public ConsumerRecord<K, V> previous() {
    return delegateListIterator.previous();
  }

  @Override
  public int nextIndex() {
    return delegateListIterator.nextIndex();
  }

  @Override
  public int previousIndex() {
    return delegateListIterator.previousIndex();
  }

  @Override
  public void remove() {
    delegateListIterator.remove();
  }

  @Override
  public void set(ConsumerRecord<K, V> consumerRecord) {
    delegateListIterator.set(consumerRecord);
  }

  @Override
  public void add(ConsumerRecord<K, V> consumerRecord) {
    delegateListIterator.add(consumerRecord);
  }
}
