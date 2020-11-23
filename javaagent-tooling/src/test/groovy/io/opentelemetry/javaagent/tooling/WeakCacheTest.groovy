/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import java.util.concurrent.Callable
import spock.lang.Specification

class WeakCacheTest extends Specification {
  def supplier = new CounterSupplier()

  def weakCache = AgentTooling.newWeakCache()
  def weakCacheFor1elem = AgentTooling.newWeakCache(1)

  def "getOrCreate a value"() {
    when:
    def count = weakCache.get('key', supplier)

    then:
    count == 1
    supplier.counter == 1
    weakCache.cache.size() == 1
  }

  def "getOrCreate a value multiple times same class loader same key"() {
    when:
    def count1 = weakCache.get('key', supplier)
    def count2 = weakCache.get('key', supplier)

    then:
    count1 == 1
    count2 == 1
    supplier.counter == 1
    weakCache.cache.size() == 1
  }

  def "getOrCreate a value multiple times same class loader different keys"() {
    when:
    def count1 = weakCache.get('key1', supplier)
    def count2 = weakCache.get('key2', supplier)

    then:
    count1 == 1
    count2 == 2
    supplier.counter == 2
    weakCache.cache.size() == 2
  }

  def "max size check"() {
    when:
    def sizeBefore = weakCacheFor1elem.cache.size()
    def valBefore = weakCacheFor1elem.getIfPresent("key1")
    def sizeAfter = weakCacheFor1elem.cache.size()
    def valAfterGet = weakCacheFor1elem.getIfPresentOrCompute("key1", supplier)
    def sizeAfterCompute = weakCacheFor1elem.cache.size()
    weakCacheFor1elem.put("key1", 42)
    def valAfterPut = weakCacheFor1elem.getIfPresentOrCompute("key1", supplier)
    def valByKey2 = weakCacheFor1elem.getIfPresentOrCompute("key2", supplier)
    def valAfterReplace = weakCacheFor1elem.getIfPresent("key1")

    then:
    valBefore == null
    valAfterGet == 1
    sizeBefore == 0
    sizeAfter == 0
    sizeAfterCompute == 1
    valAfterPut == 42
    valByKey2 == 2
    valAfterReplace == null
    weakCacheFor1elem.cache.size() == 1
  }

  static class CounterSupplier implements Callable<Integer> {
    def counter = 0

    @Override
    Integer call() {
      counter = counter + 1
      return counter
    }
  }
}
