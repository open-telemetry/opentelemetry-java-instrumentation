/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache.concurrentlinkedhashmap;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A listener registered for notification when an entry is evicted. An instance may be called
 * concurrently by multiple threads to process entries. An implementation should avoid performing
 * blocking calls or synchronizing on shared resources.
 *
 * <p>The listener is invoked by {@link ConcurrentLinkedHashMap} on a caller's thread and will not
 * block other threads from operating on the map. An implementation should be aware that the
 * caller's thread will not expect long execution times or failures as a side effect of the listener
 * being notified. Execution safety and a fast turn around time can be achieved by performing the
 * operation asynchronously, such as by submitting a task to an {@link
 * java.util.concurrent.ExecutorService}.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *     http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@ThreadSafe
public interface EvictionListener<K, V> {

  /**
   * A call-back notification that the entry was evicted.
   *
   * @param key the entry's key
   * @param value the entry's value
   */
  void onEviction(K key, V value);
}
