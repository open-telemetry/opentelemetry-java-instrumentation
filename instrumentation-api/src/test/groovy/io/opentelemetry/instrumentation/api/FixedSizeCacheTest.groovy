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

package io.opentelemetry.instrumentation.api

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicInteger
import spock.lang.Specification
import spock.util.concurrent.AsyncConditions

class FixedSizeCacheTest extends Specification {
  def "should store and retrieve values"() {
    setup:
    def fsCache = new FixedSizeCache<TKey, String>(15)
    def creationCount = new AtomicInteger(0)
    def tvc = new TVC(creationCount)
    def tk1 = new TKey(1, 1, "one")
    def tk6 = new TKey(6, 6, "six")
    def tk10 = new TKey(10, 10, "ten")
    // insert some values that happen to be the chain of hashes 1 -> 6 -> 10
    fsCache.computeIfAbsent(tk1, tvc)
    fsCache.computeIfAbsent(tk6, tvc)
    fsCache.computeIfAbsent(tk10, tvc)

    expect:
    fsCache.computeIfAbsent(tk, tvc) == value
    creationCount.get() == count

    where:
    tk                        | value          | count
    new TKey(1, 1, "foo")     | "one_value"    | 3     // used the cached tk1
    new TKey(1, 6, "foo")     | "six_value"    | 3     // used the cached tk6
    new TKey(1, 10, "foo")    | "ten_value"    | 3     // used the cached tk10
    new TKey(6, 6, "foo")     | "six_value"    | 3     // used the cached tk6
    new TKey(1, 11, "eleven") | "eleven_value" | 4     // create new value in an occupied slot
    new TKey(4, 4, "four")    | "four_value"   | 4     // create new value in empty slot
    null                      | null           | 3     // do nothing
  }

  def "should handle concurrent usage"() {
    setup:
    def numThreads = 5
    def numInsertions = 100000
    def conds = new AsyncConditions(numThreads)
    def started = new CountDownLatch(numThreads)
    def runTest = new CountDownLatch(1)
    def fsCache = new FixedSizeCache<TKey, String>(64)

    when:
    for (int t = 0; t < numThreads; t++) {
      Thread.start {
        def tlr = ThreadLocalRandom.current()
        started.countDown()
        runTest.await()
        conds.evaluate {
          for (int i = 0; i < numInsertions; i++) {
            def r = tlr.nextInt()
            def s = String.valueOf(r)
            def tkey = new TKey(r, r, s)
            assert fsCache.computeIfAbsent(tkey, { k -> k.string + "_value" }) == s + "_value"
          }
        }
      }
    }
    started.await()
    runTest.countDown()

    then:
    conds.await(30.0) // the test is really fast locally, but I don't know how fast CI is
  }

  private class TVC implements Function<TKey, String> {
    private final AtomicInteger count

    TVC(AtomicInteger count) {
      this.count = count
    }

    @Override
    String apply(TKey key) {
      count.incrementAndGet()
      return key.string + "_value"
    }
  }

  private class TKey {
    private final int hash
    private final int eq
    private final String string

    TKey(int hash, int eq, String string) {
      this.hash = hash
      this.eq = eq
      this.string = string
    }

    boolean equals(o) {
      if (getClass() != o.class) {
        return false
      }

      TKey tKey = (TKey) o

      return eq == tKey.eq
    }

    int hashCode() {
      return hash
    }

    String toString() {
      return string
    }
  }
}
