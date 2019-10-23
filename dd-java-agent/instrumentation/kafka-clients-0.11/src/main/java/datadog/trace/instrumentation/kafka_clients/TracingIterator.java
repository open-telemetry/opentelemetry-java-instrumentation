package datadog.trace.instrumentation.kafka_clients;

import static datadog.trace.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.instrumentation.api.AgentTracer.propagate;
import static datadog.trace.instrumentation.api.AgentTracer.startSpan;
import static datadog.trace.instrumentation.kafka_clients.TextMapExtractAdapter.GETTER;

import datadog.trace.instrumentation.api.AgentScope;
import datadog.trace.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.api.AgentSpan.Context;
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
  private AgentScope currentScope;

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
    if (currentScope != null) {
      currentScope.close();
      currentScope = null;
    }
    return delegateIterator.hasNext();
  }

  @Override
  public ConsumerRecord next() {
    if (currentScope != null) {
      // in case they didn't call hasNext()...
      currentScope.close();
      currentScope = null;
    }

    final ConsumerRecord next = delegateIterator.next();

    try {
      if (next != null) {
        final Context spanContext = propagate().extract(next.headers(), GETTER);
        final AgentSpan span = startSpan(operationName, spanContext);
        decorator.afterStart(span);
        decorator.onConsume(span, next);
        currentScope = activateSpan(span, true);
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
