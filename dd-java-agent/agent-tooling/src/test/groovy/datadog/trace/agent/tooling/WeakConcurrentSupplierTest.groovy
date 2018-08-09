package datadog.trace.agent.tooling

import datadog.trace.agent.test.TestUtils
import datadog.trace.bootstrap.WeakMap
import spock.lang.Specification

class WeakConcurrentSupplierTest extends Specification {
  def supplier = new WeakMapSuppliers.WeakConcurrent()

  def setup() {
    WeakMap.Provider.provider.set(supplier)
  }

  def "calling new adds to the list"() {
    when:
    def map1 = WeakMap.Provider.newWeakMap().map

    then:
    supplier.SUPPLIED_MAPS.iterator().next().get() == map1

    when:
    def map2 = WeakMap.Provider.newWeakMap().map
    def iterator = supplier.SUPPLIED_MAPS.iterator()

    then:
    iterator.next().get() == map1
    iterator.next().get() == map2
  }

  def "calling cleanMaps does cleanup"() {
    setup:
    def map = WeakMap.Provider.newWeakMap()
    map.put(new Object(), "value")
    TestUtils.awaitGC()

    expect:
    map.size() == 1 // This might result in an error if supplier's cleaner thread is activated.

    when:
    supplier.cleanMaps()

    then:
    map.size() == 0
  }
}
