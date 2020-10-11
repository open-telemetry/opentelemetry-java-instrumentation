/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api

import spock.lang.Specification

class CallDepthThreadLocalMapTest extends Specification {

  def "test CallDepthThreadLocalMap"() {
    setup:
    Class<?> k1 = String
    Class<?> k2 = Integer
    Class<?> k3 = Double

    expect:
    CallDepthThreadLocalMap.incrementCallDepth(k1) == 0
    CallDepthThreadLocalMap.incrementCallDepth(k2) == 0

    CallDepthThreadLocalMap.incrementCallDepth(k1) == 1
    CallDepthThreadLocalMap.incrementCallDepth(k2) == 1

    when:
    CallDepthThreadLocalMap.reset(k1)

    then:
    CallDepthThreadLocalMap.incrementCallDepth(k2) == 2

    when:
    CallDepthThreadLocalMap.reset(k2)

    then:
    CallDepthThreadLocalMap.incrementCallDepth(k1) == 0
    CallDepthThreadLocalMap.incrementCallDepth(k2) == 0

    CallDepthThreadLocalMap.incrementCallDepth(k1) == 1
    CallDepthThreadLocalMap.incrementCallDepth(k2) == 1

    expect:
    CallDepthThreadLocalMap.decrementCallDepth(k1) == 1
    CallDepthThreadLocalMap.decrementCallDepth(k2) == 1

    CallDepthThreadLocalMap.decrementCallDepth(k1) == 0
    CallDepthThreadLocalMap.decrementCallDepth(k2) == 0

    and:
    CallDepthThreadLocalMap.incrementCallDepth(k3) == 0
    CallDepthThreadLocalMap.decrementCallDepth(k3) == 0

  }
}
