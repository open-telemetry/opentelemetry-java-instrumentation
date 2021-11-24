/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright Rafael Winterhalter
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

// Suppress warnings since this is vendored as-is.
// CHECKSTYLE:OFF

package io.opentelemetry.instrumentation.api.cache.weaklockfree;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A thread-safe map with weak keys. Entries are based on a key's system hash code and keys are
 * considered equal only by reference equality. This class offers an abstract-base implementation
 * that allows to override methods. This class does not implement the {@link Map} interface because
 * this implementation is incompatible with the map contract. While iterating over a map's entries,
 * any key that has not passed iteration is referenced non-weakly.
 *
 * <p>This class has been copied as is from
 * https://github.com/raphw/weak-lock-free/blob/ad0e5e0c04d4a31f9485bf12b89afbc9d75473b3/src/main/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMap.java
 */
// Suppress warnings since this is vendored as-is.
@SuppressWarnings({"MissingSummary", "EqualsBrokenForNull", "FieldMissingNullable"})
abstract class AbstractWeakConcurrentMap<K, V, L> extends ReferenceQueue<K>
    implements Runnable, Iterable<Map.Entry<K, V>> {

  final ConcurrentMap<WeakKey<K>, V> target;

  protected AbstractWeakConcurrentMap() {
    this(new ConcurrentHashMap<>());
  }

  /** @param target ConcurrentMap implementation that this class wraps. */
  protected AbstractWeakConcurrentMap(ConcurrentMap<WeakKey<K>, V> target) {
    this.target = target;
  }

  /**
   * Override with care as it can cause lookup failures if done incorrectly. The result must have
   * the same {@link Object#hashCode()} as the input and be {@link Object#equals(Object) equal to} a
   * weak reference of the key. When overriding this, also override {@link #resetLookupKey}.
   */
  protected abstract L getLookupKey(K key);

  /** Resets any reusable state in the {@linkplain #getLookupKey lookup key}. */
  protected abstract void resetLookupKey(L lookupKey);

  /**
   * @param key The key of the entry.
   * @return The value of the entry or the default value if it did not exist.
   */
  public V get(K key) {
    if (key == null) {
      throw new NullPointerException();
    }
    V value;
    L lookupKey = getLookupKey(key);
    try {
      value = target.get(lookupKey);
    } finally {
      resetLookupKey(lookupKey);
    }
    if (value == null) {
      value = defaultValue(key);
      if (value != null) {
        V previousValue = target.putIfAbsent(new WeakKey<>(key, this), value);
        if (previousValue != null) {
          value = previousValue;
        }
      }
    }
    return value;
  }

  /**
   * @param key The key of the entry.
   * @return The value of the entry or null if it did not exist.
   */
  public V getIfPresent(K key) {
    if (key == null) {
      throw new NullPointerException();
    }
    L lookupKey = getLookupKey(key);
    try {
      return target.get(lookupKey);
    } finally {
      resetLookupKey(lookupKey);
    }
  }

  /**
   * @param key The key of the entry.
   * @return {@code true} if the key already defines a value.
   */
  public boolean containsKey(K key) {
    if (key == null) {
      throw new NullPointerException();
    }
    L lookupKey = getLookupKey(key);
    try {
      return target.containsKey(lookupKey);
    } finally {
      resetLookupKey(lookupKey);
    }
  }

  /**
   * @param key The key of the entry.
   * @param value The value of the entry.
   * @return The previous entry or {@code null} if it does not exist.
   */
  public V put(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException();
    }
    return target.put(new WeakKey<>(key, this), value);
  }

  /**
   * @param key The key of the entry.
   * @param value The value of the entry.
   * @return The previous entry or {@code null} if it does not exist.
   */
  public V putIfAbsent(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException();
    }
    V previous;
    L lookupKey = getLookupKey(key);
    try {
      previous = target.get(lookupKey);
    } finally {
      resetLookupKey(lookupKey);
    }
    return previous == null ? target.putIfAbsent(new WeakKey<>(key, this), value) : previous;
  }

  /**
   * @param key The key of the entry.
   * @param value The value of the entry.
   * @return The previous entry or {@code null} if it does not exist.
   */
  public V putIfProbablyAbsent(K key, V value) {
    if (key == null || value == null) {
      throw new NullPointerException();
    }
    return target.putIfAbsent(new WeakKey<>(key, this), value);
  }

  /**
   * @param key The key of the entry.
   * @return The removed entry or {@code null} if it does not exist.
   */
  public V remove(K key) {
    if (key == null) {
      throw new NullPointerException();
    }
    L lookupKey = getLookupKey(key);
    try {
      return target.remove(lookupKey);
    } finally {
      resetLookupKey(lookupKey);
    }
  }

  /** Clears the entire map. */
  public void clear() {
    target.clear();
  }

  /**
   * Creates a default value. There is no guarantee that the requested value will be set as a once
   * it is created in case that another thread requests a value for a key concurrently.
   *
   * @param key The key for which to create a default value.
   * @return The default value for a key without value or {@code null} for not defining a default
   *     value.
   */
  protected V defaultValue(K key) {
    return null;
  }

  /** Cleans all unused references. */
  public void expungeStaleEntries() {
    Reference<?> reference;
    while ((reference = poll()) != null) {
      target.remove(reference);
    }
  }

  /**
   * Returns the approximate size of this map where the returned number is at least as big as the
   * actual number of entries.
   *
   * @return The minimum size of this map.
   */
  public int approximateSize() {
    return target.size();
  }

  @Override
  public void run() {
    try {
      while (!Thread.interrupted()) {
        target.remove(remove());
      }
    } catch (InterruptedException ignored) {
      // do nothing
    }
  }

  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return new EntryIterator(target.entrySet().iterator());
  }

  @Override
  public String toString() {
    return target.toString();
  }

  /*
   * Why this works:
   * ---------------
   *
   * Note that this map only supports reference equality for keys and uses system hash codes. Also, for the
   * WeakKey instances to function correctly, we are voluntarily breaking the Java API contract for
   * hashCode/equals of these instances.
   *
   * System hash codes are immutable and can therefore be computed prematurely and are stored explicitly
   * within the WeakKey instances. This way, we always know the correct hash code of a key and always
   * end up in the correct bucket of our target map. This remains true even after the weakly referenced
   * key is collected.
   *
   * If we are looking up the value of the current key via WeakConcurrentMap::get or any other public
   * API method, we know that any value associated with this key must still be in the map as the mere
   * existence of this key makes it ineligible for garbage collection. Therefore, looking up a value
   * using another WeakKey wrapper guarantees a correct result.
   *
   * If we are looking up the map entry of a WeakKey after polling it from the reference queue, we know
   * that the actual key was already collected and calling WeakKey::get returns null for both the polled
   * instance and the instance within the map. Since we explicitly stored the identity hash code for the
   * referenced value, it is however trivial to identify the correct bucket. From this bucket, the first
   * weak key with a null reference is removed. Due to hash collision, we do not know if this entry
   * represents the weak key. However, we do know that the reference queue polls at least as many weak
   * keys as there are stale map entries within the target map. If no key is ever removed from the map
   * explicitly, the reference queue eventually polls exactly as many weak keys as there are stale entries.
   *
   * Therefore, we can guarantee that there is no memory leak.
   *
   * It is the responsibility of the actual map implementation to implement a lookup key that is used for
   * lookups. The lookup key must supply the same semantics as the weak key with regards to hash code.
   * The weak key invokes the latent key's equality method upon evaluation.
   */

  public static final class WeakKey<K> extends WeakReference<K> {

    private final int hashCode;

    WeakKey(K key, ReferenceQueue<? super K> queue) {
      super(key, queue);
      hashCode = System.identityHashCode(key);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof WeakKey<?>) {
        return ((WeakKey<?>) other).get() == get();
      } else {
        return other.equals(this);
      }
    }

    @Override
    public String toString() {
      return String.valueOf(get());
    }
  }

  private class EntryIterator implements Iterator<Map.Entry<K, V>> {

    private final Iterator<Map.Entry<WeakKey<K>, V>> iterator;

    private Map.Entry<WeakKey<K>, V> nextEntry;

    private K nextKey;

    private EntryIterator(Iterator<Map.Entry<WeakKey<K>, V>> iterator) {
      this.iterator = iterator;
      findNext();
    }

    private void findNext() {
      while (iterator.hasNext()) {
        nextEntry = iterator.next();
        nextKey = nextEntry.getKey().get();
        if (nextKey != null) {
          return;
        }
      }
      nextEntry = null;
      nextKey = null;
    }

    @Override
    public boolean hasNext() {
      return nextKey != null;
    }

    @Override
    public Map.Entry<K, V> next() {
      if (nextKey == null) {
        throw new NoSuchElementException();
      }
      try {
        return new SimpleEntry(nextKey, nextEntry);
      } finally {
        findNext();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class SimpleEntry implements Map.Entry<K, V> {

    private final K key;

    final Map.Entry<WeakKey<K>, V> entry;

    private SimpleEntry(K key, Map.Entry<WeakKey<K>, V> entry) {
      this.key = key;
      this.entry = entry;
    }

    @Override
    public K getKey() {
      return key;
    }

    @Override
    public V getValue() {
      return entry.getValue();
    }

    @Override
    public V setValue(V value) {
      if (value == null) {
        throw new NullPointerException();
      }
      return entry.setValue(value);
    }
  }
}
