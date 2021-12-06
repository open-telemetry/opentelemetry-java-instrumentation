/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.annotation.SayTracedHello
import io.opentracing.contrib.dropwizard.Trace

import java.util.concurrent.Callable

import static io.opentelemetry.api.trace.StatusCode.ERROR

class TraceAnnotationsTest extends AgentInstrumentationSpecification {

  def "test simple case annotations"() {
    setup:
    // Test single span in new trace
    SayTracedHello.sayHello()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello.sayHello"
          hasNoParent()
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "sayHello"
            "myattr" "test"
          }
        }
      }
    }
  }

  def "test complex case annotations"() {
    when:
    // Test new trace with 2 children spans
    SayTracedHello.sayHelloSayHa()

    then:
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "SayTracedHello.sayHelloSayHa"
          hasNoParent()
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "sayHelloSayHa"
            "myattr" "test2"
          }
        }
        span(1) {
          name "SayTracedHello.sayHello"
          childOf span(0)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "sayHello"
            "myattr" "test"
          }
        }
        span(2) {
          name "SayTracedHello.sayHello"
          childOf span(0)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "sayHello"
            "myattr" "test"
          }
        }
      }
    }
  }

  def "test exception exit"() {
    setup:
    Throwable error = null
    try {
      SayTracedHello.sayError()
    } catch (final Throwable ex) {
      error = ex
    }

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello.sayError"
          status ERROR
          errorEvent(error.class)
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name
            "$SemanticAttributes.CODE_FUNCTION" "sayError"
          }
        }
      }
    }
  }

  def "test anonymous class annotations"() {
    setup:
    // Test anonymous classes with package.
    SayTracedHello.fromCallable()

    expect:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello\$1.call"
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name + '$1'
            "$SemanticAttributes.CODE_FUNCTION" "call"
          }
        }
      }
    }

    when:
    // Test anonymous classes with no package.
    new Callable<String>() {
      @Trace
      @Override
      String call() throws Exception {
        return "Howdy!"
      }
    }.call()

    then:
    assertTraces(2) {
      trace(0, 1) {
        span(0) {
          name "SayTracedHello\$1.call"
          attributes {
            "$SemanticAttributes.CODE_NAMESPACE" SayTracedHello.name + '$1'
            "$SemanticAttributes.CODE_FUNCTION" "call"
          }
        }
        trace(1, 1) {
          span(0) {
            name "TraceAnnotationsTest\$1.call"
            attributes {
              "$SemanticAttributes.CODE_NAMESPACE" TraceAnnotationsTest.name + '$1'
              "$SemanticAttributes.CODE_FUNCTION" "call"
            }
          }
        }
      }
    }
  }
}
