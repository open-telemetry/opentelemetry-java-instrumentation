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

package io.opentelemetry.instrumentation.api.cache.internal.weaklockfree;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;

/**
 * A thread-safe map with weak keys. Entries are based on a key's system hash code and keys are
 * considered equal only by reference equality. This class does not implement the {@link
 * java.util.Map} interface because this implementation is incompatible with the map contract. While
 * iterating over a map's entries, any key that has not passed iteration is referenced non-weakly.
 *
 * <p>This class has been copied as is from
 * https://github.com/raphw/weak-lock-free/blob/ad0e5e0c04d4a31f9485bf12b89afbc9d75473b3/src/main/java/com/blogspot/mydailyjava/weaklockfree/WeakConcurrentMap.java
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// Suppress warnings since this is copied as-is.
@SuppressWarnings({
  "HashCodeToString",
  "MissingSummary",
  "UngroupedOverloads",
  "ThreadPriorityCheck",
  "FieldMissingNullable"
})
public class WeakConcurrentMap<K, V>
    extends AbstractWeakConcurrentMap<K, V, WeakConcurrentMap.LookupKey<K>> {

  /**
   * Lookup keys are cached thread-locally to avoid allocations on lookups. This is beneficial as
   * the JIT unfortunately can't reliably replace the {@link LookupKey} allocation with stack
   * allocations, even though the {@link LookupKey} does not escape.
   */
  private static final ThreadLocal<LookupKey<?>> LOOKUP_KEY_CACHE =
      new ThreadLocal<LookupKey<?>>() {
        @Override
        protected LookupKey<?> initialValue() {
          return new LookupKey<>();
        }
      };

  private static final AtomicLong ID = new AtomicLong();

  private final Thread thread;

  private final boolean reuseKeys;

  /** @param cleanerThread {@code true} if a thread should be started that removes stale entries. */
  public WeakConcurrentMap(boolean cleanerThread) {
    this(cleanerThread, isPersistentClassLoader(LookupKey.class.getClassLoader()));
  }

  /**
   * Checks whether the provided {@link ClassLoader} may be unloaded like a web application class
   * loader, for example.
   *
   * <p>If the class loader can't be unloaded, it is safe to use {@link ThreadLocal}s and to reuse
   * the {@link LookupKey}. Otherwise, the use of {@link ThreadLocal}s may lead to class loader
   * leaks as it prevents the class loader this class is loaded by to unload.
   *
   * @param classLoader The class loader to check.
   * @return {@code true} if the provided class loader can be unloaded.
   */
  private static boolean isPersistentClassLoader(ClassLoader classLoader) {
    try {
      return classLoader == null // bootstrap class loader
          || classLoader == ClassLoader.getSystemClassLoader()
          || classLoader
              == ClassLoader.getSystemClassLoader().getParent(); // ext/platfrom class loader;
    } catch (Throwable ignored) {
      return false;
    }
  }

  /**
   * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
   * @param reuseKeys {@code true} if the lookup keys should be reused via a {@link ThreadLocal}.
   *     Note that setting this to {@code true} may result in class loader leaks. See {@link
   *     #isPersistentClassLoader(ClassLoader)} for more details.
   */
  public WeakConcurrentMap(boolean cleanerThread, boolean reuseKeys) {
    this(cleanerThread, reuseKeys, new ConcurrentHashMap<>());
  }

  /**
   * @param cleanerThread {@code true} if a thread should be started that removes stale entries.
   * @param reuseKeys {@code true} if the lookup keys should be reused via a {@link ThreadLocal}.
   *     Note that setting this to {@code true} may result in class loader leaks. See {@link
   *     #isPersistentClassLoader(ClassLoader)} for more details.
   * @param target ConcurrentMap implementation that this class wraps.
   */
  public WeakConcurrentMap(
      boolean cleanerThread,
      boolean reuseKeys,
      ConcurrentMap<AbstractWeakConcurrentMap.WeakKey<K>, V> target) {
    super(target);
    this.reuseKeys = reuseKeys;
    if (cleanerThread) {
      thread = new Thread(this);
      thread.setName("weak-ref-cleaner-" + ID.getAndIncrement());
      thread.setPriority(Thread.MIN_PRIORITY);
      thread.setDaemon(true);
      thread.start();
    } else {
      thread = null;
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  protected LookupKey<K> getLookupKey(K key) {
    LookupKey<K> lookupKey;
    if (reuseKeys) {
      lookupKey = (LookupKey<K>) LOOKUP_KEY_CACHE.get();
    } else {
      lookupKey = new LookupKey<>();
    }
    return lookupKey.withValue(key);
  }

  @Override
  protected void resetLookupKey(LookupKey<K> lookupKey) {
    lookupKey.reset();
  }

  /** @return The cleaner thread or {@code null} if no such thread was set. */
  public Thread getCleanerThread() {
    return thread;
  }

  /*
   * A lookup key must only be used for looking up instances within a map. For this to work, it implements an identical contract for
   * hash code and equals as the WeakKey implementation. At the same time, the lookup key implementation does not extend WeakReference
   * and avoids the overhead that a weak reference implies.
   */

  // can't use AutoClosable/try-with-resources as this project still supports Java 6
  static final class LookupKey<K> {

    private K key;
    private int hashCode;

    LookupKey<K> withValue(K key) {
      this.key = key;
      hashCode = System.identityHashCode(key);
      return this;
    }

    /** Failing to reset a lookup key can lead to memory leaks as the key is strongly referenced. */
    void reset() {
      key = null;
      hashCode = 0;
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other instanceof WeakConcurrentMap.LookupKey<?>) {
        return ((LookupKey<?>) other).key == key;
      } else {
        return ((AbstractWeakConcurrentMap.WeakKey<?>) other).get() == key;
      }
    }

    @Override
    public int hashCode() {
      return hashCode;
    }
  }

  /**
   * A {@link WeakConcurrentMap} where stale entries are removed as a side effect of interacting
   * with this map.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public static class WithInlinedExpunction<K, V> extends WeakConcurrentMap<K, V> {

    public WithInlinedExpunction() {
      super(false);
    }

    @Override
    public V get(K key) {
      expungeStaleEntries();
      return super.get(key);
    }

    @Override
    public V getIfPresent(K key) {
      expungeStaleEntries();
      return super.getIfPresent(key);
    }

    @Override
    public boolean containsKey(K key) {
      expungeStaleEntries();
      return super.containsKey(key);
    }

    @Override
    public V put(K key, V value) {
      expungeStaleEntries();
      return super.put(key, value);
    }

    @Override
    public V putIfAbsent(K key, V value) {
      expungeStaleEntries();
      return super.putIfAbsent(key, value);
    }

    @Override
    public V remove(K key) {
      expungeStaleEntries();
      return super.remove(key);
    }

    @Override
    public Iterator<Map.Entry<K, V>> iterator() {
      expungeStaleEntries();
      return super.iterator();
    }

    @Override
    public int approximateSize() {
      expungeStaleEntries();
      return super.approximateSize();
    }
  }
}
