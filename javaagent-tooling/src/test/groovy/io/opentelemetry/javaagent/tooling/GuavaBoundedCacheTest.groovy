/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling

import com.google.common.cache.CacheBuilder
import spock.lang.Specification

class GuavaBoundedCacheTest extends Specification {

  def "test cache"() {

    when:

    def delegate = CacheBuilder.newBuilder().maximumSize(3).build()
    def cache = new GuavaBoundedCache<>(delegate)
    def fn = { x -> x.toUpperCase() }

    then:

    cache.get("foo", fn) == "FOO"
    cache.get("bar", fn) == "BAR"
    cache.get("baz", fn) == "BAZ"
    cache.get("fizz", fn) == "FIZZ"
    cache.get("buzz", fn) == "BUZZ"
    delegate.size() == 3
  }
}
