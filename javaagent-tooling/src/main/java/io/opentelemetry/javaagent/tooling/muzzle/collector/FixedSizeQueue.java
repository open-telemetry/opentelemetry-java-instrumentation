/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle.collector;

import java.util.ArrayDeque;
import java.util.Iterator;

final class FixedSizeQueue<T> implements Iterable<T> {
  private final int maxSize;
  private final ArrayDeque<T> queue;

  FixedSizeQueue(int maxSize) {
    this.maxSize = maxSize;
    this.queue = new ArrayDeque<>(maxSize);
  }

  void add(T elem) {
    if (hasMaxSize()) {
      queue.removeFirst();
    }
    queue.addLast(elem);
  }

  boolean hasMaxSize() {
    return queue.size() == maxSize;
  }

  T pop() {
    if (!queue.isEmpty()) {
      return queue.removeFirst();
    }
    return null;
  }

  void clear() {
    queue.clear();
  }

  @Override
  public Iterator<T> iterator() {
    return queue.iterator();
  }
}
