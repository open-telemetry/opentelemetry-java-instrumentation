/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import static java.util.Objects.requireNonNull;

import java.util.Comparator;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.Message;

final class TracingSpliterator implements Spliterator<Message> {
  private final Spliterator<Message> delegate;
  private final TracingList tracingList;

  static Spliterator<Message> wrap(Spliterator<Message> delegate, TracingList tracingList) {
    return new TracingSpliterator(delegate, tracingList);
  }

  private TracingSpliterator(Spliterator<Message> delegate, TracingList tracingList) {
    this.delegate = delegate;
    this.tracingList = tracingList;
  }

  @Override
  public boolean tryAdvance(Consumer<? super Message> action) {
    requireNonNull(action);
    return delegate.tryAdvance(
        message -> TracingIterator.processCallback(tracingList, message, action));
  }

  @Override
  public void forEachRemaining(Consumer<? super Message> action) {
    requireNonNull(action);
    delegate.forEachRemaining(
        message -> TracingIterator.processCallback(tracingList, message, action));
  }

  @Override
  @Nullable
  public Spliterator<Message> trySplit() {
    Spliterator<Message> split = delegate.trySplit();
    return split == null ? null : new TracingSpliterator(split, tracingList);
  }

  @Override
  public long estimateSize() {
    return delegate.estimateSize();
  }

  @Override
  public int characteristics() {
    return delegate.characteristics();
  }

  @Override
  @Nullable
  public Comparator<? super Message> getComparator() {
    return delegate.getComparator();
  }
}
