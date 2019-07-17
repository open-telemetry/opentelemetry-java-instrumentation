package datadog.trace.agent.tooling

import spock.lang.Shared
import spock.lang.Specification


class ClassLoaderScopedWeakMapTest extends Specification {

  @Shared
  def classLoader1 = new ClassLoader() {
    @Override
    Class<?> loadClass(String name) throws ClassNotFoundException {
      return super.loadClass(name)
    }
  }

  @Shared
  def classLoader2 = new ClassLoader() {
    @Override
    Class<?> loadClass(String name) throws ClassNotFoundException {
      return super.loadClass(name)
    }
  }

  def "getOrCreate a value"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new ClassLoaderScopedWeakMap()

    when:
    def count = sut.getOrCreate(classLoader1, 'key', supplier)

    then:
    count == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader same key"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new ClassLoaderScopedWeakMap()

    when:
    def count1 = sut.getOrCreate(classLoader1, 'key', supplier)
    def count2 = sut.getOrCreate(classLoader1, 'key', supplier)

    then:
    count1 == 1
    count2 == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader different keys"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new ClassLoaderScopedWeakMap()

    when:
    def count1 = sut.getOrCreate(classLoader1, 'key1', supplier)
    def count2 = sut.getOrCreate(classLoader1, 'key2', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
  }

  def "getOrCreate a value multiple times different class loader same key"() {
    setup:
    def supplier = new CounterSupplier()
    def sut = new ClassLoaderScopedWeakMap()

    when:
    def count1 = sut.getOrCreate(classLoader1, 'key', supplier)
    def count2 = sut.getOrCreate(classLoader2, 'key', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
  }

  class CounterSupplier implements ClassLoaderScopedWeakMap.Supplier {

    def counter = 0

    @Override
    Object get() {
      counter = counter + 1
      return counter
    }
  }
}
