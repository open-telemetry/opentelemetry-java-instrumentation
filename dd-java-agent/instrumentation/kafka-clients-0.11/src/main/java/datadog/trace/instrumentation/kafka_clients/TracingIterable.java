package datadog.trace.instrumentation.kafka_clients;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable implements Iterable<ConsumerRecord> {
  private final Iterable<ConsumerRecord> delegate;
  private final String operationName;
  private final KafkaDecorator decorator;

  public TracingIterable(
      final Iterable<ConsumerRecord> delegate,
      final String operationName,
      final KafkaDecorator decorator) {
    this.delegate = delegate;
    this.operationName = operationName;
    this.decorator = decorator;
  }

  @Override
  public Iterator<ConsumerRecord> iterator() {
    return new TracingIterator(delegate.iterator(), operationName, decorator);
  }
}
