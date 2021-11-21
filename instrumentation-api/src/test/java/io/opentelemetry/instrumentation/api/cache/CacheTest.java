/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CacheTest {

  @Nested
  @SuppressWarnings("ClassCanBeStatic")
  class StrongKeys {
    @Test
    void bounded() {
      Cache<String, String> cache = Cache.bounded(1);

      assertThat(cache.computeIfAbsent("bear", unused -> "roar")).isEqualTo("roar");
      cache.remove("bear");

      BoundedCache<?, ?> boundedCache = ((BoundedCache<?, ?>) cache);
      assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
      assertThat(boundedCache.size()).isEqualTo(1);

      assertThat(cache.computeIfAbsent("cat", unused -> "bark")).isEqualTo("meow");
      assertThat(boundedCache.size()).isEqualTo(1);

      cache.put("dog", "bark");
      assertThat(cache.get("dog")).isEqualTo("bark");
      assertThat(boundedCache.size()).isEqualTo(1);
      assertThat(cache.computeIfAbsent("cat", unused -> "purr")).isEqualTo("purr");
    }
  }

  @Nested
  @SuppressWarnings("ClassCanBeStatic")
  class WeakKeys {
    @SuppressWarnings("StringOperationCanBeSimplified")
    @Test
    void unbounded() {
      Cache<String, String> cache = Cache.weak();

      assertThat(cache.computeIfAbsent("bear", unused -> "roar")).isEqualTo("roar");
      cache.remove("bear");

      WeakLockFreeCache<?, ?> weakLockFreeCache = ((WeakLockFreeCache<?, ?>) cache);
      String cat = new String("cat");
      String dog = new String("dog");
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");
      assertThat(weakLockFreeCache.size()).isEqualTo(1);

      assertThat(cache.computeIfAbsent(cat, unused -> "bark")).isEqualTo("meow");
      assertThat(weakLockFreeCache.size()).isEqualTo(1);

      cache.put(dog, "bark");
      assertThat(cache.get(dog)).isEqualTo("bark");
      assertThat(cache.get(cat)).isEqualTo("meow");
      assertThat(cache.get(new String("dog"))).isNull();
      assertThat(weakLockFreeCache.size()).isEqualTo(2);
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");

      cat = null;
      System.gc();
      // Wait for GC to be reflected.
      await().untilAsserted(() -> assertThat(weakLockFreeCache.size()).isEqualTo(1));
      assertThat(cache.computeIfAbsent(dog, unused -> "bark")).isEqualTo("bark");
      dog = null;
      System.gc();
      // Wait for GC to be reflected.
      await().untilAsserted(() -> assertThat(weakLockFreeCache.size()).isEqualTo(0));
    }
  }
}
