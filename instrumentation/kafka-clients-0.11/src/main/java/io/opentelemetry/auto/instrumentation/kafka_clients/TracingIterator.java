/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.kafka_clients;

import static io.opentelemetry.auto.instrumentation.kafka_clients.KafkaDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.kafka_clients.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Iterator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Slf4j
public class TracingIterator implements Iterator<ConsumerRecord> {
  private final Iterator<ConsumerRecord> delegateIterator;
  private final String operationName;
  private final KafkaDecorator decorator;

  /**
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  private SpanWithScope currentSpanWithScope;

  public TracingIterator(
      final Iterator<ConsumerRecord> delegateIterator,
      final String operationName,
      final KafkaDecorator decorator) {
    this.delegateIterator = delegateIterator;
    this.operationName = operationName;
    this.decorator = decorator;
  }

  @Override
  public boolean hasNext() {
    if (currentSpanWithScope != null) {
      currentSpanWithScope.getSpan().end();
      currentSpanWithScope.closeScope();
      currentSpanWithScope = null;
    }
    return delegateIterator.hasNext();
  }

  @Override
  public ConsumerRecord next() {
    if (currentSpanWithScope != null) {
      // in case they didn't call hasNext()...
      currentSpanWithScope.getSpan().end();
      currentSpanWithScope.closeScope();
      currentSpanWithScope = null;
    }

    final ConsumerRecord next = delegateIterator.next();

    try {
      if (next != null) {
        final Span.Builder spanBuilder = TRACER.spanBuilder(operationName).setSpanKind(CONSUMER);
        try {
          spanBuilder.addLink(TRACER.getHttpTextFormat().extract(next.headers(), GETTER));
        } catch (final IllegalArgumentException e) {
          // Couldn't extract a context
        }
        final Span span = spanBuilder.startSpan();
        decorator.afterStart(span);
        decorator.onConsume(span, next);
        currentSpanWithScope = new SpanWithScope(span, TRACER.withSpan(span));
      }
    } catch (final Exception e) {
      log.debug("Error during decoration", e);
    }
    return next;
  }

  @Override
  public void remove() {
    delegateIterator.remove();
  }
}
