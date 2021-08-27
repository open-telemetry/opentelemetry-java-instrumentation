/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaConsumerIteratorWrapper;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.checkerframework.checker.nullness.qual.Nullable;

public class TracingIterator<K, V>
    implements Iterator<ConsumerRecord<K, V>>, KafkaConsumerIteratorWrapper<K, V> {

  private final Iterator<ConsumerRecord<K, V>> delegateIterator;
  private final Instrumenter<ConsumerRecord<?, ?>, Void> instrumenter;
  private final Context parentContext;

  /**
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  @Nullable private ConsumerRecord<?, ?> currentRequest;

  @Nullable private Context currentContext;
  @Nullable private Scope currentScope;

  public TracingIterator(
      Iterator<ConsumerRecord<K, V>> delegateIterator,
      Instrumenter<ConsumerRecord<?, ?>, Void> instrumenter) {
    this.delegateIterator = delegateIterator;
    this.instrumenter = instrumenter;
    parentContext = Context.current();
  }

  @Override
  public boolean hasNext() {
    closeScopeAndEndSpan();
    return delegateIterator.hasNext();
  }

  @Override
  public ConsumerRecord<K, V> next() {
    // in case they didn't call hasNext()...
    closeScopeAndEndSpan();

    ConsumerRecord<K, V> next = delegateIterator.next();
    if (next != null && instrumenter.shouldStart(parentContext, next)) {
      currentRequest = next;
      currentContext = instrumenter.start(parentContext, currentRequest);
      currentScope = currentContext.makeCurrent();
    }
    return next;
  }

  private void closeScopeAndEndSpan() {
    if (currentScope != null) {
      currentScope.close();
      instrumenter.end(currentContext, currentRequest, null, null);
      currentScope = null;
      currentRequest = null;
      currentContext = null;
    }
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }

  @Override
  public Iterator<ConsumerRecord<K, V>> unwrap() {
    return delegateIterator;
  }
}
