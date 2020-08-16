/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.kafkaclients;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.instrumentation.auto.kafkaclients.KafkaDecorator.TRACER;
import static io.opentelemetry.instrumentation.auto.kafkaclients.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracingIterator implements Iterator<ConsumerRecord> {

  private static final Logger log = LoggerFactory.getLogger(TracingIterator.class);

  private final Iterator<ConsumerRecord> delegateIterator;
  private final KafkaDecorator decorator;

  /**
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  private SpanWithScope currentSpanWithScope;

  public TracingIterator(
      Iterator<ConsumerRecord> delegateIterator, KafkaDecorator decorator) {
    this.delegateIterator = delegateIterator;
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

    ConsumerRecord next = delegateIterator.next();

    try {
      if (next != null) {
        boolean consumer = !TRACER.getCurrentSpan().getContext().isValid();
        Span.Builder spanBuilder = TRACER.spanBuilder(decorator.spanNameOnConsume(next));
        if (consumer) {
          spanBuilder.setSpanKind(CONSUMER);
        }
        if (Config.get().isKafkaClientPropagationEnabled()) {
          SpanContext spanContext = extract(next.headers(), GETTER);
          if (spanContext.isValid()) {
            if (consumer) {
              spanBuilder.setParent(spanContext);
            } else {
              spanBuilder.addLink(spanContext);
            }
          }
        }
        long startTimeMillis = System.currentTimeMillis();
        spanBuilder.setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTimeMillis));
        Span span = spanBuilder.startSpan();
        // tombstone checking logic here because it can only be inferred
        // from the record itself
        if (next.value() == null && !next.headers().iterator().hasNext()) {
          span.setAttribute("tombstone", true);
        }
        decorator.afterStart(span);
        decorator.onConsume(span, startTimeMillis, next);
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
