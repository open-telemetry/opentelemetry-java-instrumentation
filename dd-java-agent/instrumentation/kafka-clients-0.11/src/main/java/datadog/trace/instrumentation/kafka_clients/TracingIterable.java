package datadog.trace.instrumentation.kafka_clients;

import java.util.Iterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingIterable implements Iterable<ConsumerRecord> {
  private final Iterable<ConsumerRecord> delegate;
  private final String operationName;
  private final KafkaDecorator decorator;
  private boolean firstIterator = true;

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
    Iterator<ConsumerRecord> it;
    // We should only return one iterator with tracing.
    // However, this is not thread-safe, but usually the first (hopefully only) traversal of
    // ConsumerRecords is performed in the same thread that called poll()
    if (this.firstIterator) {
      it = new TracingIterator(delegate.iterator(), operationName, decorator);
      firstIterator = false;
    } else {
      it = delegate.iterator();
    }

    return it;
  }
}
