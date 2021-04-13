/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class AkkaActorTest extends AgentInstrumentationSpecification {

  def "akka #testMethod #count"() {
    setup:
    AkkaActors akkaTester = new AkkaActors()
    count.times {
      akkaTester."$testMethod"()
    }

    expect:
    assertTraces(count) {
      count.times {
        trace(it, 2) {
          span(0) {
            name "parent"
            attributes {
            }
          }
          span(1) {
            name "$expectedGreeting, Akka"
            childOf span(0)
            attributes {
            }
          }
        }
      }
    }

    where:
    testMethod     | expectedGreeting | count
    "basicTell"    | "Howdy"          | 1
    "basicAsk"     | "Howdy"          | 1
    "basicForward" | "Hello"          | 1
    "basicTell"    | "Howdy"          | 150
    "basicAsk"     | "Howdy"          | 150
    "basicForward" | "Hello"          | 150
  }
}
