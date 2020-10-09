/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.instrumentation.api.decorator.BaseDecorator.extract;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.KafkaDecorator.TRACER;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.TextMapExtractAdapter.GETTER;
import static io.opentelemetry.trace.Span.Kind.CONSUMER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;

import io.grpc.Context;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
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
  private final boolean propagationEnabled;

  /**
   * Note: this may potentially create problems if this iterator is used from different threads. But
   * at the moment we cannot do much about this.
   */
  private SpanWithScope currentSpanWithScope;

  public TracingIterator(Iterator<ConsumerRecord> delegateIterator, KafkaDecorator decorator) {
    this.delegateIterator = delegateIterator;
    this.decorator = decorator;
    this.propagationEnabled = KafkaClientConfiguration.isPropagationEnabled();
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
        if (propagationEnabled) {
          Context context = extract(next.headers(), GETTER);
          SpanContext spanContext = getSpan(context).getContext();
          if (spanContext.isValid()) {
            if (consumer) {
              spanBuilder.setParent(context);
            } else {
              spanBuilder.addLink(spanContext);
            }
          }
        }
        long startTimeMillis = System.currentTimeMillis();
        spanBuilder.setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTimeMillis));
        Span span = spanBuilder.startSpan();
        if (next.value() == null) {
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
