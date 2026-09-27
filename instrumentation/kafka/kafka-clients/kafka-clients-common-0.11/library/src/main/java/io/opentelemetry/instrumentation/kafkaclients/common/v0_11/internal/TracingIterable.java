/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.util.Iterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class TracingIterable<K, V> implements Iterable<ConsumerRecord<K, V>> {
  private final Iterable<ConsumerRecord<K, V>> delegate;
  protected final Instrumenter<KafkaProcessRequest, Void> instrumenter;
  protected final BooleanSupplier wrappingEnabled;
  protected final KafkaConsumerContext consumerContext;
  private boolean firstIterator = true;

  protected TracingIterable(
      Iterable<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    this.delegate = delegate;
    this.instrumenter = instrumenter;
    this.wrappingEnabled = wrappingEnabled;
    this.consumerContext = consumerContext;
  }

  public static <K, V> Iterable<ConsumerRecord<K, V>> wrap(
      Iterable<ConsumerRecord<K, V>> delegate,
      Instrumenter<KafkaProcessRequest, Void> instrumenter,
      BooleanSupplier wrappingEnabled,
      KafkaConsumerContext consumerContext) {
    if (!wrappingEnabled.getAsBoolean()) {
      return delegate;
    }
    return new TracingIterable<>(delegate, instrumenter, wrappingEnabled, consumerContext);
  }

  @Override
  public Iterator<ConsumerRecord<K, V>> iterator() {
    Iterator<ConsumerRecord<K, V>> iterator = delegate.iterator();
    if (firstIterator) {
      Iterator<ConsumerRecord<K, V>> tracingIterator =
          TracingIterator.wrap(iterator, instrumenter, wrappingEnabled, consumerContext);
      firstIterator = false;
      return tracingIterator;
    }
    return iterator;
  }

  @Override
  public void forEach(Consumer<? super ConsumerRecord<K, V>> action) {
    requireNonNull(action);
    iterator().forEachRemaining(action);
  }
}
