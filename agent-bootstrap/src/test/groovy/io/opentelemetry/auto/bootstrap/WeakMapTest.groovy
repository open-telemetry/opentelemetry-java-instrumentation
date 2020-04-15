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
package io.opentelemetry.auto.bootstrap

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

  class CounterSupplier implements WeakMap.ValueSupplier<String, Integer> {

    def counter = 0

    @Override
    Integer get(String ignored) {
      counter = counter + 1
      return counter
    }
  }
}
