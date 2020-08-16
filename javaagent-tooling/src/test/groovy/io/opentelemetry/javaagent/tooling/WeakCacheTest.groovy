/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

  class CounterSupplier implements Callable<Integer> {
    def counter = 0

    @Override
    Integer call() {
      counter = counter + 1
      return counter
    }
  }
}
