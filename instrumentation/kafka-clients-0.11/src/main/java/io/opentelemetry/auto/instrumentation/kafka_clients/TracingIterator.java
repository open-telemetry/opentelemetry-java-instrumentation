package io.opentelemetry.auto.instrumentation.kafka_clients;

import static io.opentelemetry.auto.instrumentation.kafka_clients.KafkaDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.kafka_clients.TextMapExtractAdapter.GETTER;

import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
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
        final Span.Builder spanBuilder = TRACER.spanBuilder(operationName);
        try {
          final SpanContext extractedContext =
              TRACER.getHttpTextFormat().extract(next.headers(), GETTER);
          spanBuilder.setParent(extractedContext);
        } catch (final IllegalArgumentException e) {
          // Couldn't extract a context. We should treat this as a root span.
          spanBuilder.setNoParent();
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
