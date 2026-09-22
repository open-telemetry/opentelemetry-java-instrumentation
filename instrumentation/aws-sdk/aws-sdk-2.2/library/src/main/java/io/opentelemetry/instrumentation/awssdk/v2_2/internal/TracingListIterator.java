/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.util.ListIterator;
import software.amazon.awssdk.services.sqs.model.Message;

final class TracingListIterator extends TracingIterator implements ListIterator<Message> {
  private final ListIterator<Message> delegate;

  static ListIterator<Message> wrap(ListIterator<Message> delegate, TracingList tracingList) {
    return new TracingListIterator(delegate, tracingList);
  }

  private TracingListIterator(ListIterator<Message> delegate, TracingList tracingList) {
    super(delegate, tracingList);
    this.delegate = delegate;
  }

  @Override
  public boolean hasPrevious() {
    closeScopeAndEndSpan();
    return delegate.hasPrevious();
  }

  @Override
  public Message previous() {
    closeScopeAndEndSpan();
    Message previous = delegate.previous();
    startProcessing(previous);
    return previous;
  }

  @Override
  public int nextIndex() {
    return delegate.nextIndex();
  }

  @Override
  public int previousIndex() {
    return delegate.previousIndex();
  }

  @Override
  public void set(Message message) {
    delegate.set(message);
  }

  @Override
  public void add(Message message) {
    delegate.add(message);
  }
}
