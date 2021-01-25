/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

class GuavaBoundedCacheTest {

  void testCache() {
    fail("OH MY");
    Cache<String, String> delegate = CacheBuilder.newBuilder().maximumSize(3).build();
    GuavaBoundedCache<String, String> cache = new GuavaBoundedCache<>(delegate);
    assertThat(cache.get("foo", String::toUpperCase)).isEqualTo("FOO");
    assertThat(cache.get("bar", String::toUpperCase)).isEqualTo("BAR");
    assertThat(cache.get("baz", String::toUpperCase)).isEqualTo("BAZ");
    assertThat(cache.get("fizz", String::toUpperCase)).isEqualTo("FIZZ");
    assertThat(cache.get("buzz", String::toUpperCase)).isEqualTo("BUZZ");
    assertThat(delegate.size()).isEqualTo(3);
  }
}
