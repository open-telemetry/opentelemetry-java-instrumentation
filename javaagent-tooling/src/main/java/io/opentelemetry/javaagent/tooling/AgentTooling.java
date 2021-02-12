/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import io.opentelemetry.javaagent.bootstrap.WeakCache;
import io.opentelemetry.javaagent.bootstrap.WeakCache.Provider;
import io.opentelemetry.javaagent.instrumentation.api.BoundedCache;
import io.opentelemetry.javaagent.instrumentation.api.WeakMap;
import io.opentelemetry.javaagent.tooling.bytebuddy.AgentCachingPoolStrategy;
import io.opentelemetry.javaagent.tooling.bytebuddy.AgentLocationStrategy;
import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * This class contains class references for objects shared by the agent installer as well as muzzle
 * (both compile and runtime). Extracted out from AgentInstaller to begin separating some of the
 * logic out.
 */
public class AgentTooling {

  static {
    // WeakMap is used by other classes below, so we need to register the provider first.
    registerWeakMapProvider();
  }

  static void registerWeakMapProvider() {
    if (!WeakMap.Provider.isProviderRegistered()) {
      WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.WeakConcurrent.Inline());
      //    WeakMap.Provider.registerIfAbsent(new WeakMapSuppliers.Guava());
    }
  }

  /**
   * Instances of BoundCache are backed by a guava instance that lives in the agent classloader and
   * is bridged to user/instrumentation classloader through the BoundedCache.Provider interface.
   */
  static void registerBoundedCacheProvider() {
    BoundedCache.Provider.registerIfAbsent(
        new BoundedCache.Builder() {
          @Override
          public <K, V> BoundedCache<K, V> build(long maxSize) {
            Cache<K, V> cache = CacheBuilder.newBuilder().maximumSize(maxSize).build();
            return new GuavaBoundedCache<>(cache);
          }
        });
  }

  private static <K, V> Provider loadWeakCacheProvider() {
    Iterator<Provider> providers =
        ServiceLoader.load(Provider.class, AgentInstaller.class.getClassLoader()).iterator();
    if (providers.hasNext()) {
      Provider provider = providers.next();
      if (providers.hasNext()) {
        throw new IllegalStateException(
            "Only one implementation of WeakCache.Provider suppose to be in classpath");
      }
      return provider;
    }
    throw new IllegalStateException("Can't load implementation of WeakCache.Provider");
  }

  private static final Provider weakCacheProvider = loadWeakCacheProvider();

  private static final AgentLocationStrategy LOCATION_STRATEGY = new AgentLocationStrategy();
  private static final AgentCachingPoolStrategy POOL_STRATEGY = new AgentCachingPoolStrategy();

  public static <K, V> WeakCache<K, V> newWeakCache() {
    return weakCacheProvider.newWeakCache();
  }

  public static <K, V> WeakCache<K, V> newWeakCache(long maxSize) {
    return weakCacheProvider.newWeakCache(maxSize);
  }

  public static AgentLocationStrategy locationStrategy() {
    return LOCATION_STRATEGY;
  }

  public static AgentCachingPoolStrategy poolStrategy() {
    return POOL_STRATEGY;
  }
}
