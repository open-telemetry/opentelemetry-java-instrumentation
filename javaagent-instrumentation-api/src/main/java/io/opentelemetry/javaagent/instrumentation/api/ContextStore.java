/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api;

import io.opentelemetry.instrumentation.api.caching.Cache;
import java.util.function.Function;

/**
 * Interface to represent context storage for instrumentations.
 *
 * <p>Context instances are weakly referenced and will be garbage collected when their corresponding
 * key instance is collected.
 *
 * @param <K> key type to do context lookups
 * @param <C> context type
 */
public interface ContextStore<K, C> {

  /**
   * Factory interface to create context instances.
   *
   * @param <C> context type
   */
  @FunctionalInterface
  interface Factory<C> {

    /** Returns a new context instance. */
    C create();
  }

  /**
   * Get context given the key.
   *
   * @param key the key to lookup
   * @return context object
   */
  C get(K key);

  /**
   * Put new context instance for given key.
   *
   * @param key key to use
   * @param context context instance to save
   */
  void put(K key, C context);

  /**
   * Put new context instance if key is absent.
   *
   * @param key key to use
   * @param context new context instance to put
   * @return old instance if it was present, or new instance
   */
  C putIfAbsent(K key, C context);

  /**
   * Put new context instance if key is absent. Uses context factory to avoid creating objects if
   * not needed.
   *
   * @param key key to use
   * @param contextFactory factory instance to produce new context object
   * @return old instance if it was present, or new instance
   */
  C putIfAbsent(K key, Factory<C> contextFactory);

  /**
   * Adapt this context store instance to {@link Cache} interface.
   *
   * @return {@link Cache} backed by this context store instance
   */
  default Cache<K, C> asCache() {
    return new Cache<K, C>() {
      @Override
      public C computeIfAbsent(K key, Function<? super K, ? extends C> mappingFunction) {
        return ContextStore.this.putIfAbsent(key, () -> mappingFunction.apply(key));
      }

      @Override
      public C get(K key) {
        return ContextStore.this.get(key);
      }

      @Override
      public void put(K key, C value) {
        ContextStore.this.put(key, value);
      }

      @Override
      public void remove(K key) {
        throw new UnsupportedOperationException("remove not supported");
      }
    };
  }
}
