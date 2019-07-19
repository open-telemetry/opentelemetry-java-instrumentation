package datadog.trace.bootstrap

import spock.lang.Specification

class WeakMapTest extends Specification {


  def "getOrCreate a value"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new WeakMap.MapAdapter<String, Integer>(new WeakHashMap<>())

    when:
    def count = sut.getOrCreate('key', supplier)

    then:
    count == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader same key"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new WeakMap.MapAdapter<String, Integer>(new WeakHashMap<>())

    when:
    def count1 = sut.getOrCreate('key', supplier)
    def count2 = sut.getOrCreate('key', supplier)

    then:
    count1 == 1
    count2 == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader different keys"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new WeakMap.MapAdapter<String, Integer>(new WeakHashMap<>())

    when:
    def count1 = sut.getOrCreate('key1', supplier)
    def count2 = sut.getOrCreate('key2', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
  }

  class CounterSupplier implements WeakMap.ValueSupplier<Integer> {

    def counter = 0

    @Override
    Integer get() {
      counter = counter + 1
      return counter
    }
  }
}
