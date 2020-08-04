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

package io.opentelemetry.auto.tooling

import io.opentelemetry.auto.util.gc.GCUtils
import io.opentelemetry.auto.util.test.AgentSpecification
import io.opentelemetry.instrumentation.auto.api.WeakMap
import spock.lang.Retry
import spock.lang.Shared

import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

@Retry
// These tests fail sometimes in CI.
class WeakConcurrentSupplierTest extends AgentSpecification {
  @Shared
  def weakConcurrentSupplier = new WeakMapSuppliers.WeakConcurrent()
  @Shared
  def weakInlineSupplier = new WeakMapSuppliers.WeakConcurrent.Inline()
  @Shared
  def guavaSupplier = new WeakMapSuppliers.Guava()

  def "Calling newWeakMap on #name creates independent maps"() {
    setup:
    WeakMap.Provider.provider.set(supplier)
    def key = new Object()
    def map1 = WeakMap.Provider.newWeakMap()
    def map2 = WeakMap.Provider.newWeakMap()

    when:
    map1.put(key, "value1")
    map2.put(key, "value2")

    then:
    map1.get(key) == "value1"
    map2.get(key) == "value2"

    where:
    name             | supplier
    "WeakConcurrent" | weakConcurrentSupplier
    "WeakInline"     | weakInlineSupplier
    "Guava"          | guavaSupplier
  }

  def "Unreferenced supplier gets cleaned up on #name"() {
    setup:
    // Note: we use 'double supplier' here because Groovy keeps reference to test data preventing it from being GCed
    def supplier = supplierSupplier()
    def ref = new WeakReference(supplier)

    when:
    def supplierRef = new WeakReference(supplier)
    supplier = null
    GCUtils.awaitGC(supplierRef)

    then:
    ref.get() == null

    where:
    name             | supplierSupplier
    "WeakConcurrent" | { -> new WeakMapSuppliers.WeakConcurrent() }
    "WeakInline"     | { -> new WeakMapSuppliers.WeakConcurrent.Inline() }
    "Guava"          | { -> new WeakMapSuppliers.Guava() }
  }

  def "Unreferenced map gets cleaned up on #name"() {
    setup:
    WeakMap.Provider.provider.set(supplier)
    def map = WeakMap.Provider.newWeakMap()
    def ref = new WeakReference(map)

    when:
    def mapRef = new WeakReference(map)
    map = null
    GCUtils.awaitGC(mapRef)

    then:
    ref.get() == null

    where:
    name             | supplier
    "WeakConcurrent" | weakConcurrentSupplier
    "WeakInline"     | weakInlineSupplier
    "Guava"          | guavaSupplier
  }

  def "Unreferenced keys get cleaned up on #name"() {
    setup:
    def key = new Object()
    map.put(key, "value")
    GCUtils.awaitGC()

    expect:
    map.size() == 1

    when:
    def keyRef = new WeakReference(key)
    key = null
    GCUtils.awaitGC(keyRef)

    if (name == "WeakConcurrent") {
      // Sleep enough time for cleanup thread to get scheduled.
      // But on a very slow box (or high load) scheduling may not be exactly predictable
      // so we try a few times.
      int count = 0
      while (map.size() != 0 && count < 10) {
        Thread.sleep(TimeUnit.SECONDS.toMillis(WeakMapSuppliers.WeakConcurrent.CLEAN_FREQUENCY_SECONDS))
        count++
      }
    }

    // Hit map a few times to trigger unreferenced entries cleanup.
    // Exact number of times that we need to hit map is implementation dependent.
    // For Guava it is specified in
    // com.google.common.collect.MapMakerInternalMap.DRAIN_THRESHOLD = 0x3F
    if (name == "Guava" || name == "WeakInline") {
      for (int i = 0; i <= 0x3F; i++) {
        map.get("test")
      }
    }

    then:
    map.size() == 0

    where:
    name             | map
    "WeakConcurrent" | weakConcurrentSupplier.get()
    "WeakInline"     | weakInlineSupplier.get()
    // Guava's cleanup process depends on concurrency level,
    // and in order to be able to test it we need to set concurrency to 1
    "Guava"          | guavaSupplier.get(1)
  }
}
