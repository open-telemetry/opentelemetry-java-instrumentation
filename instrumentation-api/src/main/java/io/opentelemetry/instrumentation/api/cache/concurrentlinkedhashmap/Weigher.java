/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache.concurrentlinkedhashmap;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A class that can determine the weight of a value. The total weight threshold is used to determine
 * when an eviction is required.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 * @see <a href="http://code.google.com/p/concurrentlinkedhashmap/">
 *     http://code.google.com/p/concurrentlinkedhashmap/</a>
 */
@ThreadSafe
public interface Weigher<V> {

  /**
   * Measures an object's weight to determine how many units of capacity that the value consumes. A
   * value must consume a minimum of one unit.
   *
   * @param value the object to weigh
   * @return the object's weight
   */
  int weightOf(V value);
}
