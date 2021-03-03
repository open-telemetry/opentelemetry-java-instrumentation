/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.caching;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CacheTest {

  @Nested
  class StrongKeys {
    @Test
    void unbounded() {
      Cache<String, String> cache = Cache.newBuilder().build();

      CaffeineCache<?, ?> caffeineCache = ((CaffeineCache<?, ?>) cache);
      assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent("cat", unused -> "bark")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent("dog", unused -> "bark")).isEqualTo("bark");
      assertThat(caffeineCache.keySet()).hasSize(2);
      assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
    }

    @Test
    void bounded() {
      Cache<String, String> cache = Cache.newBuilder().setMaximumSize(1).build();

      CaffeineCache<?, ?> caffeineCache = ((CaffeineCache<?, ?>) cache);
      assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent("cat", unused -> "bark")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent("dog", unused -> "bark")).isEqualTo("bark");
      caffeineCache.cleanup();
      assertThat(caffeineCache.keySet()).hasSize(1);
      assertThat(cache.computeIfAbsent("cat", unused -> "purr")).isEqualTo("purr");
    }
  }

  @Nested
  class WeakKeys {
    @Test
    void unbounded() {
      Cache<String, String> cache = Cache.newBuilder().setWeakKeys().build();

      CaffeineCache<?, ?> caffeineCache = ((CaffeineCache<?, ?>) cache);
      String cat = new String("cat");
      String dog = new String("dog");
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent(cat, unused -> "bark")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent(dog, unused -> "bark")).isEqualTo("bark");
      assertThat(caffeineCache.keySet()).hasSize(2);
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");

      cat = null;
      System.gc();
      // Wait for GC to be reflected.
      await()
          .untilAsserted(
              () -> {
                caffeineCache.cleanup();
                assertThat(caffeineCache.keySet()).hasSize(1);
              });
      assertThat(cache.computeIfAbsent(dog, unused -> "bark")).isEqualTo("bark");
      dog = null;
      System.gc();
      // Wait for GC to be reflected.
      await()
          .untilAsserted(
              () -> {
                caffeineCache.cleanup();
                assertThat(caffeineCache.keySet()).isEmpty();
              });
    }

    @Test
    void bounded() throws Exception {
      Cache<String, String> cache = Cache.newBuilder().setWeakKeys().setMaximumSize(1).build();

      CaffeineCache<?, ?> caffeineCache = ((CaffeineCache<?, ?>) cache);

      String cat = new String("cat");
      String dog = new String("dog");
      assertThat(cache.computeIfAbsent(cat, unused -> "meow")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent(cat, unused -> "bark")).isEqualTo("meow");
      assertThat(caffeineCache.keySet()).hasSize(1);

      assertThat(cache.computeIfAbsent(dog, unused -> "bark")).isEqualTo("bark");
      caffeineCache.cleanup();
      assertThat(caffeineCache.keySet()).hasSize(1);
      dog = null;
      System.gc();
      // Wait for GC to be reflected.
      await()
          .untilAsserted(
              () -> {
                caffeineCache.cleanup();
                assertThat(caffeineCache.keySet()).isEmpty();
              });
    }
  }
}
