/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

class BoundedCacheTest {
  private AtomicInteger cacheMisses;

  @BeforeEach
  void setup() {
    cacheMisses = new AtomicInteger(0);
  }

  @AfterEach
  void reset() {
    BoundedCache.Provider.reset();
  }

  String mockLookupFunction(String s) {
    cacheMisses.incrementAndGet();
    return s.toUpperCase();
  }

  @Test
  @Order(1)
  void testCanUseBeforeRegister() {
    BoundedCache<String, String> cache = BoundedCache.build(3);
    String result1 = cache.get("foo", this::mockLookupFunction);
    String result2 = cache.get("bAr", this::mockLookupFunction);
    assertThat(result1).isEqualTo("FOO");
    assertThat(result2).isEqualTo("BAR");
    assertThat(cacheMisses.get()).isEqualTo(2);
  }

  @Test
  @Order(2)
  void testRegisterUsesInstance() {
    Map<String, String> map = new HashMap<>();
    BoundedCache.Builder builder = buildMapBackedBuilder(map);
    BoundedCache.Provider.registerIfAbsent(builder);
    BoundedCache<String, String> cache = BoundedCache.build(3);
    String result1 = cache.get("foo", this::mockLookupFunction);
    String result2 = cache.get("fOo", this::mockLookupFunction);
    String result3 = cache.get("foo", this::mockLookupFunction);
    assertThat(result1).isEqualTo("FOO");
    assertThat(result2).isEqualTo("FOO");
    assertThat(result3).isEqualTo("FOO");
    assertThat(cacheMisses.get()).isEqualTo(2); // once for "foo" once for "fOo"
    assertThat(map.size()).isEqualTo(2);
    assertThat(map).containsKey("foo");
    assertThat(map).containsKey("fOo");
  }

  @Test
  @Order(3)
  void testRegisterMultipleFails() {
    Map<String, String> map = new HashMap<>();
    BoundedCache.Builder builder = buildMapBackedBuilder(map);
    assertThat(BoundedCache.Provider.registerIfAbsent(builder)).isTrue();
    assertThat(BoundedCache.Provider.registerIfAbsent(builder)).isFalse();
  }

  @NotNull
  private BoundedCache.Builder buildMapBackedBuilder(Map map) {
    return new BoundedCache.Builder() {
      @Override
      public <K, V> BoundedCache<K, V> build(long maxSize) {
        return new MapBackedCache<K, V>((Map<K, V>) map);
      }
    };
  }

  private static class MapBackedCache<K, V> implements BoundedCache<K, V> {
    private final Map<K, V> map;

    public MapBackedCache(Map<K, V> map) {
      this.map = map;
    }

    @Override
    public V get(K key, Function<? super K, ? extends V> mappingFunction) {
      V v = map.get(key);
      if (v == null) {
        v = mappingFunction.apply(key);
        map.put(key, v);
      }
      return v;
    }
  }
}
