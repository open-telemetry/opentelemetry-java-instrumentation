package datadog.trace.bootstrap

import datadog.trace.agent.test.TestUtils
import spock.lang.Specification

class WeakMapManagerTest extends Specification {

  def setup() {
    WeakMapManager.maps.clear()
  }

  def "calling new adds to the list"() {
    when:
    def map1 = WeakMapManager.newWeakMap()

    then:
    WeakMapManager.maps == [map1]

    when:
    def map2 = WeakMapManager.newWeakMap()

    then:
    WeakMapManager.maps == [map1, map2]
  }

  def "calling cleanMaps does cleanup"() {
    setup:
    def map = WeakMapManager.newWeakMap()
    map.put(new Object(), "value")
    TestUtils.awaitGC()

    expect:
    map.approximateSize() == 1

    when:
    WeakMapManager.cleanMaps()

    then:
    map.approximateSize() == 0
  }
}
