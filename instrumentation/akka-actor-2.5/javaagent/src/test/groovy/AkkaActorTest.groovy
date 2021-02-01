/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification

class AkkaActorTest extends AgentInstrumentationSpecification {

  // TODO this test doesn't really depend on otel.instrumentation.akka-actor.enabled=true
  //  but setting this property here is needed when running both this test
  //  and AkkaExecutorInstrumentationTest in the run, otherwise get
  //  "class redefinition failed: attempted to change superclass or interfaces"
  //  on whichever test runs second
  //  (related question is what's the purpose of this test if it doesn't depend on any of the
  //  instrumentation in this module, is it just to verify that the instrumentation doesn't break
  //  this scenario?)
  static {
    System.setProperty("otel.instrumentation.akka-actor.enabled", "true")
  }

  def "akka #testMethod"() {
    setup:
    AkkaActors akkaTester = new AkkaActors()
    akkaTester."$testMethod"()

    expect:
    assertTraces(1) {
      trace(0, 2) {
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

    where:
    testMethod     | expectedGreeting
    "basicTell"    | "Howdy"
    "basicAsk"     | "Howdy"
    "basicForward" | "Hello"
  }
}
