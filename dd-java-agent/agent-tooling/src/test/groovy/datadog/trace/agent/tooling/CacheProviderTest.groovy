package datadog.trace.agent.tooling

import datadog.trace.util.test.DDSpecification
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.dynamic.ClassFileLocator
import net.bytebuddy.pool.TypePool
import spock.lang.Timeout

import java.security.SecureClassLoader

@Timeout(5)
class CacheProviderTest extends DDSpecification {
  def "key equivalence"() {
    setup:
    def key1 = new DDCachingPoolStrategy.TypeCacheKey(1, "foo")
    def key2 = new DDCachingPoolStrategy.TypeCacheKey(1, "foo")

    expect:
    key1.hashCode() == key2.hashCode()
    key1.equals(key2)
  }

  def "different loader - same name"() {
    setup:
    def key1 = new DDCachingPoolStrategy.TypeCacheKey(1, "foo")
    def key2 = new DDCachingPoolStrategy.TypeCacheKey(2, "foo")

    expect:
    // not strictly guaranteed, but important for performance
    key1.hashCode() != key2.hashCode()

    !key1.equals(key2)
  }

  def "same loader - different name"() {
    setup:
    def key1 = new DDCachingPoolStrategy.TypeCacheKey(1, "foo")
    def key2 = new DDCachingPoolStrategy.TypeCacheKey(1, "foobar")

    expect:
    // not strictly guaranteed, but important for performance
    key1.hashCode() != key2.hashCode()

    !key1.equals(key2)
  }

  def "test basic caching"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()

    def cacheProvider = cacheInstance.createCacheProvider(1)

    when:
    cacheProvider.register("foo", new TypePool.Resolution.Simple(TypeDescription.VOID))

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider.find("foo") != null
    cacheInstance.approximateSize() == 1
  }

  def "test ID equivalence"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()

    def cacheProvider1A = cacheInstance.createCacheProvider(1)
    def cacheProvider1B = cacheInstance.createCacheProvider(1)

    when:
    cacheProvider1A.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1A.find("foo") != null
    cacheProvider1B.find("foo") != null

    cacheProvider1A.find("foo").is(cacheProvider1B.find("foo"))
    cacheInstance.approximateSize() == 1
  }

  def "test ID separation"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()

    def cacheProvider1 = cacheInstance.createCacheProvider(1)
    def cacheProvider2 = cacheInstance.createCacheProvider(2)

    when:
    cacheProvider1.register("foo", newVoid())
    cacheProvider2.register("foo", newVoid())

    then:
    // not strictly guaranteed, but fine for this test
    cacheProvider1.find("foo") != null
    cacheProvider2.find("foo") != null

    !cacheProvider1.find("foo").is(cacheProvider2.find("foo"))
    cacheInstance.approximateSize() == 2
  }

  def "test loader ID assignment"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()

    def locator1 = newLocator()
    def loader1 = newClassLoader()

    def locator2 = newLocator()
    def loader2 = newClassLoader()

    when:
    cacheInstance.typePool(locator1, loader1)
    cacheInstance.typePool(locator2, loader2)

    then:
    def loaderId1 = cacheInstance.loaderIdCache.getIfPresent(loader1)
    def loaderId2 = cacheInstance.loaderIdCache.getIfPresent(loader2)

    // both were assigned an ID -- technically these can fall out of the ID cache
    loaderId1 != null
    loaderId2 != null

    // both IDs are not the BOOTSTRAP_ID
    loaderId1 != DDCachingPoolStrategy.CacheInstance.BOOTSTRAP_ID
    loaderId2 != DDCachingPoolStrategy.CacheInstance.BOOTSTRAP_ID

    // class loaders don't share an ID
    cacheInstance.loaderIdCache.getIfPresent(loader1) != cacheInstance.loaderIdCache.getIfPresent(loader2)
  }

  def "test loader ID exhaustion"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()

    when:
    cacheInstance.loaderIdSeq.set(DDCachingPoolStrategy.CacheInstance.LIMIT_ID - 2)

    then:
    cacheInstance.provisionId() != DDCachingPoolStrategy.CacheInstance.EXHAUSTED_ID

    then:
    // once exhausted provisioning -- stays exhausted
    cacheInstance.provisionId() == DDCachingPoolStrategy.CacheInstance.EXHAUSTED_ID
    cacheInstance.exhaustedLoaderIdSeq()
    cacheInstance.provisionId() == DDCachingPoolStrategy.CacheInstance.EXHAUSTED_ID
    cacheInstance.exhaustedLoaderIdSeq()
    cacheInstance.provisionId() == DDCachingPoolStrategy.CacheInstance.EXHAUSTED_ID
    cacheInstance.exhaustedLoaderIdSeq()
  }

  def "test exhaustion cacheInstance switch"() {
    setup:
    def cachingStrat = new DDCachingPoolStrategy()
    def origCacheInstance = cachingStrat.cacheInstance

    cachingStrat.cacheInstance.loaderIdSeq.set(DDCachingPoolStrategy.CacheInstance.LIMIT_ID)

    when:
    cachingStrat.typePool(newLocator(), newClassLoader())

    then:
    cachingStrat.cacheInstance != origCacheInstance
  }

  def "test cacheInstance capacity"() {
    setup:
    def cacheInstance = new DDCachingPoolStrategy.CacheInstance()
    def capacity = DDCachingPoolStrategy.CacheInstance.TYPE_CAPACITY

    def cacheProvider1 = cacheInstance.createCacheProvider(1)
    def cacheProvider2 = cacheInstance.createCacheProvider(2)

    def id = 0

    when:
    (capacity / 2).times {
      id += 1
      cacheProvider1.register("foo${id}", newVoid())
      cacheProvider2.register("foo${id}", newVoid())
    }

    then:
    // cache will start to proactively free slots & size calc is approximate
    cacheInstance.approximateSize() > capacity - 4

    when:
    10.times {
      id += 1
      cacheProvider1.register("foo${id}", newVoid())
      cacheProvider2.register("foo${id}", newVoid())
    }

    then:
    // cache will start to proactively free slots & size calc is approximate
    cacheInstance.approximateSize() > capacity - 4
  }

  static newVoid() {
    return new TypePool.Resolution.Simple(TypeDescription.VOID)
  }

  static newClassLoader() {
    return new SecureClassLoader(null) {}
  }

  static newLocator() {
    return new ClassFileLocator() {
      @Override
      ClassFileLocator.Resolution locate(String name) throws IOException {
        return null
      }

      @Override
      void close() throws IOException {}
    }
  }
}
