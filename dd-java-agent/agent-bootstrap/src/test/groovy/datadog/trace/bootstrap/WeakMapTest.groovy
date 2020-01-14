package datadog.trace.bootstrap

import spock.lang.Specification

class WeakMapTest extends Specification {

  def supplier = new CounterSupplier()

  def sut = new WeakMap.MapAdapter<String, Integer>(new WeakHashMap<>())

  def "getOrCreate a value"() {
    when:
    def count = sut.computeIfAbsent('key', supplier)

    then:
    count == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader same key"() {
    when:
    def count1 = sut.computeIfAbsent('key', supplier)
    def count2 = sut.computeIfAbsent('key', supplier)

    then:
    count1 == 1
    count2 == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader different keys"() {
    when:
    def count1 = sut.computeIfAbsent('key1', supplier)
    def count2 = sut.computeIfAbsent('key2', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
  }

  class CounterSupplier implements WeakMap.ValueSupplier<String, Integer> {

    def counter = 0

    @Override
    Integer get(String ignored) {
      counter = counter + 1
      return counter
    }
  }
}
