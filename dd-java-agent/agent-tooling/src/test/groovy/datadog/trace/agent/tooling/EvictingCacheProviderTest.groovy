package datadog.trace.agent.tooling

import datadog.trace.util.gc.GCUtils
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.pool.TypePool
import spock.lang.Specification
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@Timeout(5)
class EvictingCacheProviderTest extends Specification {
  def "test provider"() {
    setup:
    def provider = new DDCachingPoolStrategy.EvictingCacheProvider(2, TimeUnit.MINUTES)

    expect:
    provider.size() == 0
    provider.find(className) == null

    when:
    provider.register(className, new TypePool.Resolution.Simple(TypeDescription.VOID))

    then:
    provider.size() == 1
    provider.find(className) == new TypePool.Resolution.Simple(TypeDescription.VOID)

    when:
    provider.clear()

    then:
    provider.size() == 0
    provider.find(className) == null

    where:
    className = "SomeClass"
  }

  def "test timeout eviction"() {
    setup:
    def provider = new DDCachingPoolStrategy.EvictingCacheProvider(10, TimeUnit.MILLISECONDS)
    def resolutionRef = new AtomicReference<TypePool.Resolution>(new TypePool.Resolution.Simple(TypeDescription.VOID))
    def weakRef = new WeakReference(resolutionRef.get())

    when:
    provider.register(className, resolutionRef.get())

    then:
    provider.size() == 1
    Thread.sleep(10)
    provider.find(className) == null

    when:
    provider.register(className, resolutionRef.get())
    resolutionRef.set(null)
    GCUtils.awaitGC(weakRef)

    then:
    provider.size() == 0
    weakRef.get() == null

    where:
    className = "SomeClass"
  }

  def "test size limit"() {
    setup:
    def provider = new DDCachingPoolStrategy.EvictingCacheProvider(2, TimeUnit.MINUTES)
    def typeDef = new TypePool.Resolution.Simple(TypeDescription.VOID)
    for (int i = 0; i < 20000; i++) {
      provider.register("ClassName$i", typeDef)
    }

    expect:
    provider.size() == 10000

    when:
    provider.clear()

    then:
    provider.size() == 0
  }
}
