import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import io.dropwizard.testing.junit.ResourceTestRule
import org.jboss.resteasy.core.Dispatcher
import org.jboss.resteasy.mock.MockDispatcherFactory
import org.jboss.resteasy.mock.MockHttpRequest
import org.jboss.resteasy.mock.MockHttpResponse
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Unroll

import javax.ws.rs.client.Entity
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.PreMatching
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

@Unroll
abstract class JaxRsFilterTest extends AgentTestRunner {

  @Shared
  SimpleRequestFilter simpleRequestFilter = new SimpleRequestFilter()

  @Shared
  PrematchRequestFilter prematchRequestFilter = new PrematchRequestFilter()

  abstract makeRequest(String url)

  def "test #resource, #abortNormal, #abortPrematch"() {
    given:
    simpleRequestFilter.abort = abortNormal
    prematchRequestFilter.abort = abortPrematch
    def abort = abortNormal || abortPrematch

    when:
    def responseText
    def responseStatus

    // start a trace because the test doesn't go through any servlet or other instrumentation.
    runUnderTrace("test.span") {
      (responseText, responseStatus) = makeRequest(resource)
    }

    then:
    responseText == expectedResponse

    if (abort) {
      responseStatus == Response.Status.UNAUTHORIZED.statusCode
    } else {
      responseStatus == Response.Status.OK.statusCode
    }

    assertTraces(1) {
      trace(0, 2) {
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
          operationName abort ? "jax-rs.request.abort" : "jax-rs.request"
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
    resource           | abortNormal | abortPrematch | parentResourceName         | controllerName                 | expectedResponse
    "/test/hello/bob"  | false       | false         | "POST /test/hello/{name}"  | "Test1.hello"                  | "Test1 bob!"
    "/test2/hello/bob" | false       | false         | "POST /test2/hello/{name}" | "Test2.hello"                  | "Test2 bob!"
    "/test3/hi/bob"    | false       | false         | "POST /test3/hi/{name}"    | "Test3.hello"                  | "Test3 bob!"

    // Resteasy and Jersey give different resource class names for just the below case
    // Resteasy returns "SubResource.class"
    // Jersey returns "Test1.class
    // "/test/hello/bob"  | true        | false         | "POST /test/hello/{name}"  | "Test1.hello"                  | "Aborted"

    "/test2/hello/bob" | true        | false         | "POST /test2/hello/{name}" | "Test2.hello"                  | "Aborted"
    "/test3/hi/bob"    | true        | false         | "POST /test3/hi/{name}"    | "Test3.hello"                  | "Aborted"
    "/test/hello/bob"  | false       | true          | "test.span"                | "PrematchRequestFilter.filter" | "Aborted Prematch"
    "/test2/hello/bob" | false       | true          | "test.span"                | "PrematchRequestFilter.filter" | "Aborted Prematch"
    "/test3/hi/bob"    | false       | true          | "test.span"                | "PrematchRequestFilter.filter" | "Aborted Prematch"
  }

  def "test nested call"() {
    given:
    simpleRequestFilter.abort = false
    prematchRequestFilter.abort = false

    when:
    def responseText
    def responseStatus

    // start a trace because the test doesn't go through any servlet or other instrumentation.
    runUnderTrace("test.span") {
      (responseText, responseStatus) = makeRequest(resource)
    }

    then:
    responseStatus == Response.Status.OK.statusCode
    responseText == expectedResponse

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

  @Provider
  class SimpleRequestFilter implements ContainerRequestFilter {
    boolean abort = false

    @Override
    void filter(ContainerRequestContext requestContext) throws IOException {
      if (abort) {
        requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED)
            .entity("Aborted")
            .type(MediaType.TEXT_PLAIN_TYPE)
            .build())
      }
    }
  }

  @Provider
  @PreMatching
  class PrematchRequestFilter implements ContainerRequestFilter {
    boolean abort = false

    @Override
    void filter(ContainerRequestContext requestContext) throws IOException {
      if (abort) {
        requestContext.abortWith(
          Response.status(Response.Status.UNAUTHORIZED)
            .entity("Aborted Prematch")
            .type(MediaType.TEXT_PLAIN_TYPE)
            .build())
      }
    }
  }
}

class JerseyFilterTest extends JaxRsFilterTest {
  @Shared
  @ClassRule
  ResourceTestRule resources = ResourceTestRule.builder()
    .addResource(new Resource.Test1())
    .addResource(new Resource.Test2())
    .addResource(new Resource.Test3())
    .addProvider(simpleRequestFilter)
    .addProvider(prematchRequestFilter)
    .build()

  @Override
  def makeRequest(String url) {
    Response response = resources.client().target(url).request().post(Entity.text(""))

    return [response.readEntity(String), response.statusInfo.statusCode]
  }
}

class ResteasyFilterTest extends JaxRsFilterTest {
  @Shared
  Dispatcher dispatcher

  def setupSpec() {
    dispatcher = MockDispatcherFactory.createDispatcher()
    def registry = dispatcher.getRegistry()
    registry.addSingletonResource(new Resource.Test1())
    registry.addSingletonResource(new Resource.Test2())
    registry.addSingletonResource(new Resource.Test3())

    dispatcher.getProviderFactory().register(simpleRequestFilter)
    dispatcher.getProviderFactory().register(prematchRequestFilter)
  }

  @Override
  def makeRequest(String url) {
    MockHttpRequest request = MockHttpRequest.post(url)
    request.contentType(MediaType.TEXT_PLAIN_TYPE)
    request.content(new byte[0])

    MockHttpResponse response = new MockHttpResponse()
    dispatcher.invoke(request, response)

    return [response.contentAsString, response.status]
  }

}
