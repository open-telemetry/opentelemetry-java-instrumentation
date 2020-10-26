/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api

import spock.lang.Specification

class WeakMapTest extends Specification {

  def supplier = new CounterSupplier()

  def weakMap = new WeakMap.MapAdapter<String, Integer>(new WeakHashMap<>())

  def "getOrCreate a value"() {
    when:
    def count = weakMap.computeIfAbsent('key', supplier)

    then:
    count == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader same key"() {
    when:
    def count1 = weakMap.computeIfAbsent('key', supplier)
    def count2 = weakMap.computeIfAbsent('key', supplier)

    then:
    count1 == 1
    count2 == 1
    supplier.counter == 1
  }

  def "getOrCreate a value multiple times same class loader different keys"() {
    when:
    def count1 = weakMap.computeIfAbsent('key1', supplier)
    def count2 = weakMap.computeIfAbsent('key2', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
  }

  def "remove a value"() {
    given:
    weakMap.put('key', 42)

    when:
    def removed = weakMap.remove('key')

    then:
    removed == 42
    weakMap.get('key') == null
    weakMap.size() == 0
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
