/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** An LRU cache that has a fixed maximum size. */
public interface BoundedCache<K, V> {

  V get(K key, Function<? super K, ? extends V> mappingFunction);

  static <K, V> BoundedCache<K, V> build(long maxSize) {
    return Provider.get().build(maxSize);
  }

  interface Builder {
    <K, V> BoundedCache<K, V> build(long maxSize);
  }

  class Provider {
    /*
     The default implementation just delegates to the lookup function and should not normally be used.
     It will be replaced at startup by the AgentInstaller.
    */
    private static final Builder NEVER_ACTUALLY_CACHES =
        new Builder() {
          @Override
          public <K, V> BoundedCache<K, V> build(long maxSize) {
            return (key, mappingFunction) -> mappingFunction.apply(key);
          }
        };
    private static final AtomicReference<Builder> builderRef =
        new AtomicReference<>(NEVER_ACTUALLY_CACHES);

    private Provider() {}

    public static boolean registerIfAbsent(Builder builder) {
      return builderRef.compareAndSet(NEVER_ACTUALLY_CACHES, builder);
    }

    // Method exists for testing only
    static void reset() {
      builderRef.set(NEVER_ACTUALLY_CACHES);
    }

    public static Builder get() {
      return builderRef.get();
    }
  }
}
