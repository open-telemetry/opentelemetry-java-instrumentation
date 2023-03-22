/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import javax.annotation.Nullable;

final class ConstantSizeStack<T> {

  private final Object lock = new Object();

  @GuardedBy("lock")
  private final Object[] items;

  @GuardedBy("lock")
  private int currentIndex;

  ConstantSizeStack(int size) {
    items = new Object[size];
    // will start from 0 on the first push() call
    currentIndex = size - 1;
  }

  void push(T item) {
    synchronized (lock) {
      currentIndex = (currentIndex + 1) % items.length;
      items[currentIndex] = item;
    }
  }

  @Nullable
  T pop() {
    synchronized (lock) {
      T item = peek();
      items[currentIndex] = null;
      int size = items.length;
      currentIndex = ((currentIndex == 0 ? size : currentIndex) - 1) % size;
      return item;
    }
  }

  @Nullable
  @SuppressWarnings("unchecked")
  T peek() {
    synchronized (lock) {
      return (T) items[currentIndex];
    }
  }
}
