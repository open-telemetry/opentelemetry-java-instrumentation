/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

class CacheProviderTest {

  @Test
  void keyBootstrapEquivalence() {
    int loaderHash = AgentCachingPoolStrategy.BOOTSTRAP_HASH;
    WeakReference<ClassLoader> loaderRef = null;

    AgentCachingPoolStrategy.TypeCacheKey key1 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo");
    AgentCachingPoolStrategy.TypeCacheKey key2 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo");

    assertThat(key1).hasSameHashCodeAs(key2).isEqualTo(key2);
  }

  @Test
  void keySameRefEquivalence() {
    ClassLoader loader = newClassLoader();
    int loaderHash = loader.hashCode();
    WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);

    AgentCachingPoolStrategy.TypeCacheKey key1 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo");
    AgentCachingPoolStrategy.TypeCacheKey key2 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo");

    assertThat(key1).hasSameHashCodeAs(key2).isEqualTo(key2);
    // ensures that loader isn't collected
    assertThat(loader).isNotNull();
  }

  @Test
  void keyDifferentRefEquivalence() {
    ClassLoader loader = newClassLoader();
    int loaderHash = loader.hashCode();
    WeakReference<ClassLoader> loaderRef1 = new WeakReference<>(loader);
    WeakReference<ClassLoader> loaderRef2 = new WeakReference<>(loader);

    AgentCachingPoolStrategy.TypeCacheKey key1 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef1, "foo");
    AgentCachingPoolStrategy.TypeCacheKey key2 =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef2, "foo");

    assertThat(loaderRef1).isNotSameAs(loaderRef2);

    assertThat(key1).hasSameHashCodeAs(key2).isEqualTo(key2);
    // ensures that loader isn't collected
    assertThat(loader).isNotNull();
  }

  @Test
  void keyMismatchSameLoaderDifferentName() {
    ClassLoader loader = newClassLoader();
    int loaderHash = loader.hashCode();
    WeakReference<ClassLoader> loaderRef = new WeakReference<>(loader);
    AgentCachingPoolStrategy.TypeCacheKey fooKey =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo");
    AgentCachingPoolStrategy.TypeCacheKey barKey =
        new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "bar");

    // not strictly guaranteed -- but important for performance
    assertThat(fooKey.hashCode()).isNotEqualTo(barKey.hashCode());
    assertThat(fooKey).isNotEqualTo(barKey);
    // ensures that loader isn't collected
    assertThat(loader).isNotNull();
  }

  @Test
  void keyMismatchSameNameDifferentLoader() {
    ClassLoader loader1 = newClassLoader();
    int loader1Hash = loader1.hashCode();
    WeakReference<ClassLoader> loaderRef1 = new WeakReference<>(loader1);

    ClassLoader loader2 = newClassLoader();
    int loader2Hash = loader2.hashCode();
    WeakReference<ClassLoader> loaderRef2 = new WeakReference<>(loader2);

    AgentCachingPoolStrategy.TypeCacheKey fooKey1 =
        new AgentCachingPoolStrategy.TypeCacheKey(loader1Hash, loaderRef1, "foo");
    AgentCachingPoolStrategy.TypeCacheKey fooKey2 =
        new AgentCachingPoolStrategy.TypeCacheKey(loader2Hash, loaderRef2, "foo");

    // not strictly guaranteed -- but important for performance
    assertThat(fooKey1.hashCode()).isNotEqualTo(fooKey2.hashCode());
    assertThat(fooKey1).isNotEqualTo(fooKey2);
    // ensures that loader isn't collected
    assertThat(loader1).isNotNull();
    assertThat(loader2).isNotNull();
  }

  @Test
  void testBasicCaching() {
    AgentCachingPoolStrategy poolStrat = new AgentCachingPoolStrategy(null);

    ClassLoader loader = newClassLoader();

    TypePool.CacheProvider cacheProvider = poolStrat.getCacheProvider(loader);

    cacheProvider.register(
        "foo", new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(void.class)));

    // not strictly guaranteed, but fine for this test
    assertThat(cacheProvider.find("foo")).isNotNull();
    // ensures that loader isn't collected
    assertThat(loader).isNotNull();
  }

  @Test
  void testLoaderEquivalence() {
    AgentCachingPoolStrategy poolStrat = new AgentCachingPoolStrategy(null);

    ClassLoader loader1 = newClassLoader();

    TypePool.CacheProvider cacheProvider1A = poolStrat.getCacheProvider(loader1);
    TypePool.CacheProvider cacheProvider1B = poolStrat.getCacheProvider(loader1);

    cacheProvider1A.register("foo", newVoid());

    // not strictly guaranteed, but fine for this test
    assertThat(cacheProvider1A.find("foo")).isNotNull();
    assertThat(cacheProvider1B.find("foo")).isNotNull();

    assertThat(cacheProvider1A.find("foo")).isSameAs(cacheProvider1B.find("foo"));

    // ensures that loader isn't collected
    assertThat(loader1).isNotNull();
  }

  @Test
  void testLoaderSeparation() {
    AgentCachingPoolStrategy poolStrat = new AgentCachingPoolStrategy(null);

    ClassLoader loader1 = newClassLoader();
    ClassLoader loader2 = newClassLoader();

    TypePool.CacheProvider cacheProvider1 = poolStrat.getCacheProvider(loader1);
    TypePool.CacheProvider cacheProvider2 = poolStrat.getCacheProvider(loader2);

    cacheProvider1.register("foo", newVoid());
    cacheProvider2.register("foo", newVoid());

    // not strictly guaranteed, but fine for this test
    assertThat(cacheProvider1.find("foo")).isNotNull();
    assertThat(cacheProvider2.find("foo")).isNotNull();

    assertThat(cacheProvider1.find("foo")).isNotSameAs(cacheProvider2.find("foo"));

    // ensures that loader isn't collected
    assertThat(loader1).isNotNull();
    assertThat(loader2).isNotNull();
  }

  private static TypePool.Resolution newVoid() {
    return new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(void.class));
  }

  private static ClassLoader newClassLoader() {
    return new URLClassLoader(new URL[0], null);
  }
}
