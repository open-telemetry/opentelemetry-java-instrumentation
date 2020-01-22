import io.dropwizard.testing.junit.ResourceTestRule
import io.opentelemetry.auto.api.MoreTags
import io.opentelemetry.auto.api.SpanTypes
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import org.junit.ClassRule
import spock.lang.Shared

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderTrace

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
          tags {
            "$MoreTags.RESOURCE_NAME" expectedResourceName
            "$Tags.COMPONENT" "jax-rs"
          }
        }

        span(1) {
          childOf span(0)
          operationName "jax-rs.request"
          tags {
            "$MoreTags.RESOURCE_NAME" controllerName
            "$MoreTags.SPAN_TYPE" SpanTypes.HTTP_SERVER
            "$Tags.COMPONENT" "jax-rs-controller"
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
}
