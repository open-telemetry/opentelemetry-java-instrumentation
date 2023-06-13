/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.javaagent.tooling.muzzle.AgentCachingPoolStrategy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.pool.TypePool
import spock.lang.Specification

import java.lang.ref.WeakReference

class CacheProviderTest extends Specification {
  def "key bootstrap equivalence"() {
    // def loader = null
    def loaderHash = AgentCachingPoolStrategy.BOOTSTRAP_HASH
    def loaderRef = null

    def key1 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo")
    def key2 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo")

    expect:
    key1.hashCode() == key2.hashCode()
    key1.equals(key2)
  }

  def "key same ref equivalence"() {
    setup:
    def loader = newClassLoader()
    def loaderHash = loader.hashCode()
    def loaderRef = new WeakReference<ClassLoader>(loader)

    def key1 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo")
    def key2 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo")

    expect:
    key1.hashCode() == key2.hashCode()
    key1.equals(key2)
    // ensures that loader isn't collected
    loader != null
  }

  def "key different ref equivalence"() {
    setup:
    def loader = newClassLoader()
    def loaderHash = loader.hashCode()
    def loaderRef1 = new WeakReference<ClassLoader>(loader)
    def loaderRef2 = new WeakReference<ClassLoader>(loader)

    def key1 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef1, "foo")
    def key2 = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef2, "foo")

    expect:
    loaderRef1 != loaderRef2

    key1.hashCode() == key2.hashCode()
    key1.equals(key2)
    // ensures that loader isn't collected
    loader != null
  }

  def "key mismatch -- same loader - diff name"() {
    setup:
    def loader = newClassLoader()
    def loaderHash = loader.hashCode()
    def loaderRef = new WeakReference<ClassLoader>(loader)
    def fooKey = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "foo")
    def barKey = new AgentCachingPoolStrategy.TypeCacheKey(loaderHash, loaderRef, "bar")

    expect:
    // not strictly guaranteed -- but important for performance
    fooKey.hashCode() != barKey.hashCode()
    !fooKey.equals(barKey)
    // ensures that loader isn't collected
    loader != null
  }

  def "key mismatch -- same name - diff loader"() {
    setup:
    def loader1 = newClassLoader()
    def loader1Hash = loader1.hashCode()
    def loaderRef1 = new WeakReference<ClassLoader>(loader1)

    def loader2 = newClassLoader()
    def loader2Hash = loader2.hashCode()
    def loaderRef2 = new WeakReference<ClassLoader>(loader2)

    def fooKey1 = new AgentCachingPoolStrategy.TypeCacheKey(loader1Hash, loaderRef1, "foo")
    def fooKey2 = new AgentCachingPoolStrategy.TypeCacheKey(loader2Hash, loaderRef2, "foo")

    expect:
    // not strictly guaranteed -- but important for performance
    fooKey1.hashCode() != fooKey2.hashCode()
    !fooKey1.equals(fooKey2)
    // ensures that loader isn't collected
    loader1 != null
    loader2 != null
  }

  def "test basic caching"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader = newClassLoader()

    def cacheProvider = poolStrat.getCacheProvider(loader)

    when:
    cacheProvider.register("foo", new TypePool.Resolution.Simple(TypeDescription.VOID))

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider.find("foo") != null
    // ensures that loader isn't collected
    loader != null
  }

  def "test loader equivalence"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader1 = newClassLoader()

    def cacheProvider1A = poolStrat.getCacheProvider(loader1)
    def cacheProvider1B = poolStrat.getCacheProvider(loader1)

    when:
    cacheProvider1A.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1A.find("foo") != null
    cacheProvider1B.find("foo") != null

    cacheProvider1A.find("foo").is(cacheProvider1B.find("foo"))

    // ensures that loader isn't collected
    loader1 != null
  }

  def "test loader separation"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader1 = newClassLoader()
    def loader2 = newClassLoader()

    def cacheProvider1 = poolStrat.getCacheProvider(loader1)
    def cacheProvider2 = poolStrat.getCacheProvider(loader2)

    when:
    cacheProvider1.register("foo", newVoid())
    cacheProvider2.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1.find("foo") != null
    cacheProvider2.find("foo") != null

    !cacheProvider1.find("foo").is(cacheProvider2.find("foo"))

    // ensures that loader isn't collected
    loader1 != null
    loader2 != null
  }

  static newVoid() {
    return new TypePool.Resolution.Simple(TypeDescription.ForLoadedType.of(void.class))
  }

  static newClassLoader() {
    return new URLClassLoader([] as URL[], (ClassLoader) null)
  }
}
