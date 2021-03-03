package io.opentelemetry.instrumentation.api.caching;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BoundedCacheTest {

  @Test
  void bounded() {
    Cache<String, String> cache = Cache.newBuilder().setMaximumSize(1).build();

    CaffeineCache<?, ?> caffeineCache = ((CaffeineCache<?, ?>) cache);
    assertThat(cache.computeIfAbsent("cat", unused -> "meow")).isEqualTo("meow");
    assertThat(caffeineCache.estimatedSize()).isOne();
  }
}
