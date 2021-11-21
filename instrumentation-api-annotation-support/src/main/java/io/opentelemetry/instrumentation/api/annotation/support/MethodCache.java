/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import io.opentelemetry.instrumentation.api.cache.Cache;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Implementation of {@link Cache} that uses {@link ClassValue} to store values keyed by {@link
 * Method} compared by value equality while allowing the declaring class to be unloaded.
 */
final class MethodCache<V> extends ClassValue<Map<Method, V>> implements Cache<Method, V> {
  @Override
  public V computeIfAbsent(Method key, Function<? super Method, ? extends V> mappingFunction) {
    return this.get(key.getDeclaringClass()).computeIfAbsent(key, mappingFunction);
  }

  @Override
  public V get(Method key) {
    return this.get(key.getDeclaringClass()).get(key);
  }

  @Override
  public void put(Method key, V value) {
    this.get(key.getDeclaringClass()).put(key, value);
  }

  @Override
  public void remove(Method key) {
    this.get(key.getDeclaringClass()).remove(key);
  }

  @Override
  protected Map<Method, V> computeValue(Class<?> type) {
    return new ConcurrentHashMap<>();
  }
}
