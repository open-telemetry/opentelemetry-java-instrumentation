package io.opentelemetry.auto.tooling

import io.opentelemetry.auto.util.gc.GCUtils
import io.opentelemetry.auto.util.test.AgentSpecification
import net.bytebuddy.description.type.TypeDescription
import net.bytebuddy.pool.TypePool
import spock.lang.Timeout

import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static io.opentelemetry.auto.tooling.AgentTooling.CLEANER

@Timeout(5)
class EvictingCacheProviderTest extends AgentSpecification {

  def "test provider"() {
    setup:
    def provider = new AgentCachingPoolStrategy.EvictingCacheProvider(CLEANER, 2, TimeUnit.MINUTES)

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
    def provider = new AgentCachingPoolStrategy.EvictingCacheProvider(CLEANER, timeout, TimeUnit.MILLISECONDS)
    def resolutionRef = new AtomicReference<TypePool.Resolution>(new TypePool.Resolution.Simple(TypeDescription.VOID))
    def weakRef = new WeakReference(resolutionRef.get())

    when:
    def lastAccess = System.nanoTime()
    provider.register(className, resolutionRef.get())

    then:
    // Ensure continued access prevents expiration.
    for (int i = 0; i < timeout + 10; i++) {
      assert TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastAccess) < timeout: "test took too long on " + i
      assert provider.find(className) != null
      assert provider.size() == 1
      lastAccess = System.nanoTime()
      Thread.sleep(1)
    }

    when:
    Thread.sleep(timeout)

    then:
    provider.find(className) == null

    when:
    provider.register(className, resolutionRef.get())
    resolutionRef.set(null)
    GCUtils.awaitGC(weakRef)

    then:
    // Verify properly GC'd
    provider.find(className) == null
    weakRef.get() == null

    where:
    className = "SomeClass"
    timeout = 500 // Takes about 50 ms locally, adding an order of magnitude for CI.
  }

  def "test size limit"() {
    setup:
    def provider = new AgentCachingPoolStrategy.EvictingCacheProvider(CLEANER, 2, TimeUnit.MINUTES)
    def typeDef = new TypePool.Resolution.Simple(TypeDescription.VOID)
    for (int i = 0; i < 10000; i++) {
      provider.register("ClassName$i", typeDef)
    }

    expect:
    provider.size() == 5000

    when:
    provider.clear()

    then:
    provider.size() == 0
  }
}
