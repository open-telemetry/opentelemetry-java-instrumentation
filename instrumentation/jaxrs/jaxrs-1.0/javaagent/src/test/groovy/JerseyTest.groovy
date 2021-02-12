/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderServerTrace

import io.dropwizard.testing.junit.ResourceTestRule
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import org.junit.ClassRule
import spock.lang.Shared

class JerseyTest extends AgentInstrumentationSpecification {

  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder()
    .addResource(new Resource.Test1())
    .addResource(new Resource.Test2())
    .addResource(new Resource.Test3())
    .build()

  def "test #resource"() {
    when:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def response = runUnderServerTrace("test.span") {
      resources.client().resource(resource).post(String)
    }

    then:
    response == expectedResponse

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name expectedSpanName
          attributes {
          }
        }

        span(1) {
          childOf span(0)
          name controllerName
          attributes {
          }
        }
      }
    }

    where:
    resource           | expectedSpanName      | controllerName | expectedResponse
    "/test/hello/bob"  | "/test/hello/{name}"  | "Test1.hello"  | "Test1 bob!"
    "/test2/hello/bob" | "/test2/hello/{name}" | "Test2.hello"  | "Test2 bob!"
    "/test3/hi/bob"    | "/test3/hi/{name}"    | "Test3.hello"  | "Test3 bob!"
  }

  def "test nested call"() {

    when:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def response = runUnderServerTrace("test.span") {
      resources.client().resource(resource).post(String)
    }

    then:
    response == expectedResponse

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name expectedSpanName
          attributes {
          }
        }
        span(1) {
          childOf span(0)
          name controller1Name
          kind INTERNAL
          attributes {
          }
        }
      }
    }

    where:
    resource        | expectedSpanName | controller1Name | expectedResponse
    "/test3/nested" | "/test3/nested"  | "Test3.nested"  | "Test3 nested!"
  }
}
