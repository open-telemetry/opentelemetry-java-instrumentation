package datadog.trace.instrumentation.kafka_clients;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingList extends TracingIterable implements List<ConsumerRecord> {
  private final List<ConsumerRecord> delegate;

  public TracingList(
      final List<ConsumerRecord> delegate,
      final String operationName,
      final KafkaDecorator decorator) {
    super(delegate, operationName, decorator);
    this.delegate = delegate;
  }

  @Override
  public int size() {
    return delegate.size();
  }

  @Override
  public boolean isEmpty() {
    return delegate.isEmpty();
  }

  @Override
  public boolean contains(final Object o) {
    return delegate.contains(o);
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(final T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean add(final ConsumerRecord consumerRecord) {
    return delegate.add(consumerRecord);
  }

  @Override
  public boolean remove(final Object o) {
    return delegate.remove(o);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean addAll(final Collection<? extends ConsumerRecord> c) {
    return delegate.addAll(c);
  }

  @Override
  public boolean addAll(final int index, final Collection<? extends ConsumerRecord> c) {
    return delegate.addAll(index, c);
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ConsumerRecord get(final int index) {
    // TODO: should this be instrumented as well?
    return delegate.get(index);
  }

  @Override
  public ConsumerRecord set(final int index, final ConsumerRecord element) {
    return delegate.set(index, element);
  }

  @Override
  public void add(final int index, final ConsumerRecord element) {
    delegate.add(index, element);
  }

  @Override
  public ConsumerRecord remove(final int index) {
    return delegate.remove(index);
  }

  @Override
  public int indexOf(final Object o) {
    return delegate.indexOf(o);
  }

  @Override
  public int lastIndexOf(final Object o) {
    return delegate.lastIndexOf(o);
  }

  @Override
  public ListIterator<ConsumerRecord> listIterator() {
    // TODO: the API for ListIterator is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    return delegate.listIterator();
  }

  @Override
  public ListIterator<ConsumerRecord> listIterator(final int index) {
    // TODO: the API for ListIterator is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    return delegate.listIterator(index);
  }

  @Override
  public List<ConsumerRecord> subList(final int fromIndex, final int toIndex) {
    // TODO: the API for subList is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    // Kafka is essentially a sequential commit log. We should only enable tracing when traversing
    // sequentially with an iterator
    return delegate.subList(fromIndex, toIndex);
  }
}
