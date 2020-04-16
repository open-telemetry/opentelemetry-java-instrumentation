import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.dropwizard.testing.junit.ResourceTestRule
import org.junit.ClassRule
import spock.lang.Shared

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class JerseyTest extends AgentTestRunner {

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
    def response = runUnderTrace("test.span") {
      resources.client().resource(resource).post(String)
    }

    then:
    response == expectedResponse

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "test.span"
          resourceName expectedResourceName
          tags {
            "$Tags.COMPONENT" "jax-rs"
            defaultTags()
          }
        }

        span(1) {
          childOf span(0)
          operationName "jax-rs.request"
          resourceName controllerName
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            defaultTags()
          }
        }
      }
    }

    where:
    resource           | expectedResourceName       | controllerName | expectedResponse
    "/test/hello/bob"  | "POST /test/hello/{name}"  | "Test1.hello"  | "Test1 bob!"
    "/test2/hello/bob" | "POST /test2/hello/{name}" | "Test2.hello"  | "Test2 bob!"
    "/test3/hi/bob"    | "POST /test3/hi/{name}"    | "Test3.hello"  | "Test3 bob!"
  }

  def "test nested call"() {

    when:
    // start a trace because the test doesn't go through any servlet or other instrumentation.
    def response = runUnderTrace("test.span") {
      resources.client().resource(resource).post(String)
    }

    then:
    response == expectedResponse

    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          operationName "test.span"
          resourceName parentResourceName
          tags {
            "$Tags.COMPONENT" "jax-rs"
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          operationName "jax-rs.request"
          resourceName controller1Name
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          operationName "jax-rs.request"
          resourceName controller2Name
          spanType DDSpanTypes.HTTP_SERVER
          tags {
            "$Tags.COMPONENT" "jax-rs-controller"
            defaultTags()
          }
        }
      }
    }

    where:
    resource        | parentResourceName   | controller1Name | controller2Name | expectedResponse
    "/test3/nested" | "POST /test3/nested" | "Test3.nested"  | "Test3.hello"   | "Test3 nested!"
  }
}
