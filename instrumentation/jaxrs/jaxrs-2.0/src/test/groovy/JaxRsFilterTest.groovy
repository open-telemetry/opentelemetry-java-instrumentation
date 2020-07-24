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

import io.dropwizard.testing.junit.ResourceTestRule
import io.opentelemetry.auto.test.AgentTestRunner
import javax.ws.rs.client.Entity
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.container.PreMatching
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider
import org.jboss.resteasy.core.Dispatcher
import org.jboss.resteasy.mock.MockDispatcherFactory
import org.jboss.resteasy.mock.MockHttpRequest
import org.jboss.resteasy.mock.MockHttpResponse
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Unroll

import static io.opentelemetry.auto.test.utils.TraceUtils.runUnderServerTrace
import static io.opentelemetry.trace.Span.Kind.INTERNAL

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
    runUnderServerTrace("test.span") {
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
          operationName parentSpanName != null ? parentSpanName : "test.span"
          attributes {
          }
        }
        span(1) {
          childOf span(0)
          operationName controllerName
          attributes {
          }
        }
      }
    }

    where:
    resource           | abortNormal | abortPrematch | parentSpanName             | controllerName                 | expectedResponse
    "/test/hello/bob"  | false       | false         | "POST /test/hello/{name}"  | "Test1.hello"                  | "Test1 bob!"
    "/test2/hello/bob" | false       | false         | "POST /test2/hello/{name}" | "Test2.hello"                  | "Test2 bob!"
    "/test3/hi/bob"    | false       | false         | "POST /test3/hi/{name}"    | "Test3.hello"                  | "Test3 bob!"

    // Resteasy and Jersey give different resource class names for just the below case
    // Resteasy returns "SubResource.class"
    // Jersey returns "Test1.class
    // "/test/hello/bob"  | true        | false         | "POST /test/hello/{name}"  | "Test1.hello"                  | "Aborted"

    "/test2/hello/bob" | true        | false         | "POST /test2/hello/{name}" | "Test2.hello"                  | "Aborted"
    "/test3/hi/bob"    | true        | false         | "POST /test3/hi/{name}"    | "Test3.hello"                  | "Aborted"
    "/test/hello/bob"  | false       | true          | null                       | "PrematchRequestFilter.filter" | "Aborted Prematch"
    "/test2/hello/bob" | false       | true          | null                       | "PrematchRequestFilter.filter" | "Aborted Prematch"
    "/test3/hi/bob"    | false       | true          | null                       | "PrematchRequestFilter.filter" | "Aborted Prematch"
  }

  def "test nested call"() {
    given:
    simpleRequestFilter.abort = false
    prematchRequestFilter.abort = false

    when:
    def responseText
    def responseStatus

    // start a trace because the test doesn't go through any servlet or other instrumentation.
    runUnderServerTrace("test.span") {
      (responseText, responseStatus) = makeRequest(resource)
    }

    then:
    responseStatus == Response.Status.OK.statusCode
    responseText == expectedResponse

    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName parentResourceName
          attributes {
          }
        }
        span(1) {
          childOf span(0)
          operationName controller1Name
          spanKind INTERNAL
          attributes {
          }
        }
      }
    }

    where:
    resource        | parentResourceName   | controller1Name | expectedResponse
    "/test3/nested" | "POST /test3/nested" | "Test3.nested"  | "Test3 nested!"
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
