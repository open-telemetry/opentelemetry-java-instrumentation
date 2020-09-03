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

package io.opentelemetry.instrumentation.logback.v1_0_0;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

final class UnionSet<T> extends AbstractSet<T> {

  private final Set<T> first;
  private final Set<T> second;

  int size = -1;

  UnionSet(Set<T> first, Set<T> second) {
    this.first = first;
    this.second = second;
  }

  @Override
  public int size() {
    if (size == -1) {
      size = first.size();
      for (T item : second) {
        if (!first.contains(item)) {
          size++;
        }
      }
    }
    return size;
  }

  @Override
  public boolean isEmpty() {
    return first.isEmpty() && second.isEmpty();
  }

  @Override
  public boolean add(T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean addAll(Collection<? extends T> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<T> iterator() {
    return new Iterator<T>() {

      final Iterator<T> firstItr = first.iterator();
      final Iterator<T> secondItr = second.iterator();

      @Override
      public boolean hasNext() {
        return firstItr.hasNext() || secondItr.hasNext();
      }

      @Override
      public T next() {
        if (firstItr.hasNext()) {
          return firstItr.next();
        }
        while (secondItr.hasNext()) {
          T item = secondItr.next();
          if (!first.contains(item)) {
            return item;
          }
        }
        throw new NoSuchElementException();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
