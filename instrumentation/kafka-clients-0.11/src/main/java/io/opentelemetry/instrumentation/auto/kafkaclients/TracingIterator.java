/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.kafkaclients;

import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingIterator implements Iterator<ConsumerRecord<?, ?>> {

  private static final Logger log = LoggerFactory.getLogger(TracingIterator.class);

  private final Iterator<ConsumerRecord<?, ?>> delegateIterator;
  private final KafkaConsumerTracer tracer;
  private final boolean propagationEnabled;

  /**
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  private SpanWithScope currentSpanWithScope;

  public TracingIterator(
      Iterator<ConsumerRecord<?, ?>> delegateIterator, KafkaConsumerTracer tracer) {
    this.delegateIterator = delegateIterator;
    this.tracer = tracer;
    this.propagationEnabled = KafkaClientConfiguration.isPropagationEnabled();
  }

  @Override
  public boolean hasNext() {
    if (currentSpanWithScope != null) {
      tracer.end(currentSpanWithScope.getSpan());
      currentSpanWithScope.closeScope();
      currentSpanWithScope = null;
    }
    return delegateIterator.hasNext();
  }

  @Override
  public ConsumerRecord<?, ?> next() {
    if (currentSpanWithScope != null) {
      // in case they didn't call hasNext()...
      tracer.end(currentSpanWithScope.getSpan());
      currentSpanWithScope.closeScope();
      currentSpanWithScope = null;
    }

    ConsumerRecord<?, ?> next = delegateIterator.next();

    try {
      if (next != null) {
        Span span = tracer.startSpan(next);

        currentSpanWithScope = new SpanWithScope(span, currentContextWith(span));
      }
    } catch (Exception e) {
      log.debug("Error during decoration", e);
    }
    return next;
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
