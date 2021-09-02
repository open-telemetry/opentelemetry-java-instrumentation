/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import io.opentelemetry.javaagent.tooling.muzzle.AgentCachingPoolStrategy
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
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
  }

  def "test basic caching"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader = newClassLoader()
    def loaderHash = loader.hashCode()
    def loaderRef = new WeakReference<ClassLoader>(loader)

    def cacheProvider = poolStrat.createCacheProvider(loaderHash, loaderRef)

    when:
    cacheProvider.register("foo", new TypePool.Resolution.Simple(TypeDescription.VOID))

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider.find("foo") != null
  }

  def "test loader equivalence"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader1 = newClassLoader()
    def loaderHash1 = loader1.hashCode()
    def loaderRef1A = new WeakReference<ClassLoader>(loader1)
    def loaderRef1B = new WeakReference<ClassLoader>(loader1)

    def cacheProvider1A = poolStrat.createCacheProvider(loaderHash1, loaderRef1A)
    def cacheProvider1B = poolStrat.createCacheProvider(loaderHash1, loaderRef1B)

    when:
    cacheProvider1A.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1A.find("foo") != null
    cacheProvider1B.find("foo") != null

    cacheProvider1A.find("foo").is(cacheProvider1B.find("foo"))
  }

  def "test loader separation"() {
    setup:
    def poolStrat = new AgentCachingPoolStrategy()

    def loader1 = newClassLoader()
    def loaderHash1 = loader1.hashCode()
    def loaderRef1 = new WeakReference<ClassLoader>(loader1)

    def loader2 = newClassLoader()
    def loaderHash2 = loader2.hashCode()
    def loaderRef2 = new WeakReference<ClassLoader>(loader2)

    def cacheProvider1 = poolStrat.createCacheProvider(loaderHash1, loaderRef1)
    def cacheProvider2 = poolStrat.createCacheProvider(loaderHash2, loaderRef2)

    when:
    cacheProvider1.register("foo", newVoid())
    cacheProvider2.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1.find("foo") != null
    cacheProvider2.find("foo") != null

    !cacheProvider1.find("foo").is(cacheProvider2.find("foo"))
  }

  static newVoid() {
    return new TypePool.Resolution.Simple(TypeDescription.VOID)
  }

  static newClassLoader() {
    return new URLClassLoader([] as URL[], (ClassLoader) null)
  }

  static newLocator() {
    return new ClassFileLocator() {
      @Override
      ClassFileLocator.Resolution locate(String name) throws IOException {
        return null
      }

      @Override
      void close() throws IOException {
      }
    }
  }
}
