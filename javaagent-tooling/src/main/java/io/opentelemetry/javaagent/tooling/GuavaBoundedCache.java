/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.common.cache.Cache;
import io.opentelemetry.javaagent.instrumentation.api.BoundedCache;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

class GuavaBoundedCache<K, V> implements BoundedCache<K, V> {

  private final Cache<K, V> delegate;

  public GuavaBoundedCache(Cache<K, V> delegate) {
    this.delegate = delegate;
  }

  @Override
  public V get(K key, Function<? super K, ? extends V> mappingFunction) {
    try {
      return delegate.get(key, () -> mappingFunction.apply(key));
    } catch (ExecutionException e) {
      throw new IllegalStateException("Unexpected cache exception", e);
    }
  }
}
