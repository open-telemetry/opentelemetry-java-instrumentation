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

class CallDepthThreadLocalMapTest extends Specification {

  def "test CallDepthThreadLocalMap"() {
    setup:
    Class<?> k1 = String
    Class<?> k2 = Integer

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
  }
}
