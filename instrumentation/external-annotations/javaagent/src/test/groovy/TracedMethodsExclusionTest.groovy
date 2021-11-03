/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentracing.contrib.dropwizard.Trace

class TracedMethodsExclusionTest extends AgentInstrumentationSpecification {

  static class TestClass {
    //This method is not mentioned in any configuration
    String notMentioned() {
      return "Hello!"
    }

    //This method is both configured to be traced and to be excluded. Should NOT be traced.
    String excluded() {
      return "Hello!"
    }

    //This method is annotated with annotation which usually results in a captured span
    @Trace
    String annotated() {
      return "Hello!"
    }

    //This method is annotated with annotation which usually results in a captured span, but is configured to be
    //excluded.
    @Trace
    String annotatedButExcluded() {
      return "Hello!"
    }
  }


  //Baseline and assumption validation
  def "Calling these methods should be traced"() {
    expect:
    new TestClass().annotated() == "Hello!"

    and:
    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          name "TestClass.annotated"
          attributes {
            "${SemanticAttributes.CODE_NAMESPACE}" TestClass.name
            "${SemanticAttributes.CODE_FUNCTION}" "annotated"
          }
        }
      }
    }
  }

  def "Not explicitly configured method should not be traced"() {
    expect:
    new TestClass().notMentioned() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

  def "Method which is both annotated and excluded for tracing should NOT be traced"() {
    expect:
    new TestClass().excluded() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }

  def "Method exclusion should override tracing annotations"() {
    expect:
    new TestClass().annotatedButExcluded() == "Hello!"

    and:
    Thread.sleep(500) // sleep a bit just to make sure no span is captured
    assertTraces(0) {}
  }
}
