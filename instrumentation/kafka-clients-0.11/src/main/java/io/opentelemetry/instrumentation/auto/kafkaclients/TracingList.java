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

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public class TracingList extends TracingIterable implements List<ConsumerRecord> {
  private final List<ConsumerRecord> delegate;

  public TracingList(List<ConsumerRecord> delegate, KafkaDecorator decorator) {
    super(delegate, decorator);
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
  public boolean contains(Object o) {
    return delegate.contains(o);
  }

  @Override
  public Object[] toArray() {
    return delegate.toArray();
  }

  @Override
  public <T> T[] toArray(T[] a) {
    return delegate.toArray(a);
  }

  @Override
  public boolean add(ConsumerRecord consumerRecord) {
    return delegate.add(consumerRecord);
  }

  @Override
  public boolean remove(Object o) {
    return delegate.remove(o);
  }

  @Override
  public boolean containsAll(Collection<?> c) {
    return delegate.containsAll(c);
  }

  @Override
  public boolean addAll(Collection<? extends ConsumerRecord> c) {
    return delegate.addAll(c);
  }

  @Override
  public boolean addAll(int index, Collection<? extends ConsumerRecord> c) {
    return delegate.addAll(index, c);
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    return delegate.removeAll(c);
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    return delegate.retainAll(c);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ConsumerRecord get(int index) {
    // TODO: should this be instrumented as well?
    return delegate.get(index);
  }

  @Override
  public ConsumerRecord set(int index, ConsumerRecord element) {
    return delegate.set(index, element);
  }

  @Override
  public void add(int index, ConsumerRecord element) {
    delegate.add(index, element);
  }

  @Override
  public ConsumerRecord remove(int index) {
    return delegate.remove(index);
  }

  @Override
  public int indexOf(Object o) {
    return delegate.indexOf(o);
  }

  @Override
  public int lastIndexOf(Object o) {
    return delegate.lastIndexOf(o);
  }

  @Override
  public ListIterator<ConsumerRecord> listIterator() {
    // TODO: the API for ListIterator is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    return delegate.listIterator();
  }

  @Override
  public ListIterator<ConsumerRecord> listIterator(int index) {
    // TODO: the API for ListIterator is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    return delegate.listIterator(index);
  }

  @Override
  public List<ConsumerRecord> subList(int fromIndex, int toIndex) {
    // TODO: the API for subList is not really good to instrument it in context of Kafka
    // Consumer so we will not do that for now
    // Kafka is essentially a sequential commit log. We should only enable tracing when traversing
    // sequentially with an iterator
    return delegate.subList(fromIndex, toIndex);
  }
}
