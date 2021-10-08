/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.ClientRequestContext
import io.opentelemetry.testing.internal.armeria.client.DecoratingHttpClientFunction
import io.opentelemetry.testing.internal.armeria.client.HttpClient
import io.opentelemetry.testing.internal.armeria.client.WebClient
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames
import io.opentelemetry.testing.internal.armeria.common.HttpRequest
import io.opentelemetry.testing.internal.armeria.common.HttpResponse
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.web.server.ResponseStatusException
import server.EchoHandlerFunction
import server.FooModel
import server.SpringWebFluxTestApplication
import server.TestController
import spock.lang.Unroll

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = [SpringWebFluxTestApplication, ForceNettyAutoConfiguration])
class SpringWebfluxTest extends AgentInstrumentationSpecification {
  @TestConfiguration
  static class ForceNettyAutoConfiguration {
    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory()
    }
  }

  static final String INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX = SpringWebFluxTestApplication.getName() + "\$"
  static final String SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX = SpringWebFluxTestApplication.getSimpleName() + "\$"

  @LocalServerPort
  int port

  WebClient client

  def setup() {
    client = WebClient.builder("h1c://localhost:$port")
      .decorator(new DecoratingHttpClientFunction() {
        // https://github.com/line/armeria/issues/2489
        @Override
        HttpResponse execute(HttpClient delegate, ClientRequestContext ctx, HttpRequest req) throws Exception {
          return HttpResponse.from(delegate.execute(ctx, req).aggregate().thenApply { resp ->
            if (resp.status().isRedirection()) {
              return delegate.execute(ctx, HttpRequest.of(req.method(), resp.headers().get(HttpHeaderNames.LOCATION)))
            }
            return resp.toHttpResponse()
          })
        }
      })
      .build()
  }

  @Unroll
  def "Basic GET test #testName"() {
    when:
    def response = client.get(urlPath).aggregate().join()

    then:
    response.status().code() == 200
    response.contentUtf8() == expectedResponseBody
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name urlPathWithVariables
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port$urlPath"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            nameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            name TestController.getSimpleName() + "." + annotatedMethod
          }
          kind INTERNAL
          childOf span(0)
          attributes {
            if (annotatedMethod == null) {
              // Functional API
              "spring-webflux.handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "spring-webflux.handler.type" TestController.getName()
            }
          }
        }
      }
    }

    where:
    testName                             | urlPath              | urlPathWithVariables   | annotatedMethod | expectedResponseBody
    "functional API without parameters"  | "/greet"             | "/greet"               | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE
    "functional API with one parameter"  | "/greet/WORLD"       | "/greet/{name}"        | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " WORLD"
    "functional API with two parameters" | "/greet/World/Test1" | "/greet/{name}/{word}" | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " World Test1"
    "functional API delayed response"    | "/greet-delayed"     | "/greet-delayed"       | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE

    "annotation API without parameters"  | "/foo"               | "/foo"                 | "getFooModel"   | new FooModel(0L, "DEFAULT").toString()
    "annotation API with one parameter"  | "/foo/1"             | "/foo/{id}"            | "getFooModel"   | new FooModel(1L, "pass").toString()
    "annotation API with two parameters" | "/foo/2/world"       | "/foo/{id}/{name}"     | "getFooModel"   | new FooModel(2L, "world").toString()
    "annotation API delayed response"    | "/foo-delayed"       | "/foo-delayed"         | "getFooDelayed" | new FooModel(3L, "delayed").toString()
  }

  @Unroll
  def "GET test with async response #testName"() {
    when:
    def response = client.get(urlPath).aggregate().join()

    then:
    response.status().code() == 200
    response.contentUtf8() == expectedResponseBody
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name urlPathWithVariables
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port$urlPath"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            nameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            name TestController.getSimpleName() + "." + annotatedMethod
          }
          kind INTERNAL
          childOf span(0)
          attributes {
            if (annotatedMethod == null) {
              // Functional API
              "spring-webflux.handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "spring-webflux.handler.type" TestController.getName()
            }
          }
        }
        span(2) {
          name "tracedMethod"
          childOf span(0)
          attributes {
          }
        }
      }
    }

    where:
    testName                                  | urlPath                       | urlPathWithVariables             | annotatedMethod       | expectedResponseBody
    "functional API traced method from mono"  | "/greet-mono-from-callable/4" | "/greet-mono-from-callable/{id}" | null                  | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 4"
    "functional API traced method with delay" | "/greet-delayed-mono/6"       | "/greet-delayed-mono/{id}"       | null                  | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 6"

    "annotation API traced method from mono"  | "/foo-mono-from-callable/7"   | "/foo-mono-from-callable/{id}"   | "getMonoFromCallable" | new FooModel(7L, "tracedMethod").toString()
    "annotation API traced method with delay" | "/foo-delayed-mono/9"         | "/foo-delayed-mono/{id}"         | "getFooDelayedMono"   | new FooModel(9L, "tracedMethod").toString()
  }

  /*
  This test differs from the previous in one important aspect.
  The test above calls endpoints which does not create any spans during their invocation.
  They merely assemble reactive pipeline where some steps create spans.
  Thus all those spans are created when WebFlux span created by DispatcherHandlerInstrumentation
  has already finished. Therefore, they have `SERVER` span as their parent.

  This test below calls endpoints which do create spans right inside endpoint handler.
  Therefore, in theory, those spans should have INTERNAL span created by DispatcherHandlerInstrumentation
  as their parent. But there is a difference how Spring WebFlux handles functional endpoints
  (created in server.SpringWebFluxTestApplication.greetRouterFunction) and annotated endpoints
  (created in server.TestController).
  In the former case org.springframework.web.reactive.function.server.support.HandlerFunctionAdapter.handle
  calls handler function directly. Thus "tracedMethod" span below has INTERNAL handler span as its parent.
  In the latter case org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerAdapter.handle
  merely wraps handler call into Mono and thus actual invocation of handler function happens later,
  when INTERNAL handler span has already finished. Thus, "tracedMethod" has SERVER Netty span as its parent.
   */

  def "Create span during handler function"() {
    when:
    def response = client.get(urlPath).aggregate().join()

    then:
    response.status().code() == 200
    response.contentUtf8() == expectedResponseBody
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name urlPathWithVariables
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port$urlPath"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            nameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            name TestController.getSimpleName() + "." + annotatedMethod
          }
          kind INTERNAL
          childOf span(0)
          attributes {
            if (annotatedMethod == null) {
              // Functional API
              "spring-webflux.handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "spring-webflux.handler.type" TestController.getName()
            }
          }
        }
        span(2) {
          name "tracedMethod"
          childOf span(annotatedMethod ? 0 : 1)
          attributes {
          }
        }
      }
    }

    where:
    testName                       | urlPath                  | urlPathWithVariables        | annotatedMethod   | expectedResponseBody
    "functional API traced method" | "/greet-traced-method/5" | "/greet-traced-method/{id}" | null              | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " 5"
    "annotation API traced method" | "/foo-traced-method/8"   | "/foo-traced-method/{id}"   | "getTracedMethod" | new FooModel(8L, "tracedMethod").toString()
  }

  def "404 GET test"() {
    when:
    def response = client.get("/notfoundgreet").aggregate().join()

    then:
    response.status().code() == 404
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name "/**"
          kind SERVER
          hasNoParent()
          status ERROR
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port/notfoundgreet"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 404
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          name "ResourceWebHandler.handle"
          kind INTERNAL
          childOf span(0)
          status ERROR
          errorEvent(ResponseStatusException, String)
          attributes {
            "spring-webflux.handler.type" "org.springframework.web.reactive.resource.ResourceWebHandler"
          }
        }
      }
    }
  }

  def "Basic POST test"() {
    setup:
    String echoString = "TEST"
    when:
    def response = client.post("/echo", echoString).aggregate().join()

    then:
    response.status().code() == 202
    response.contentUtf8() == echoString
    assertTraces(1) {
      trace(0, 3) {
        span(0) {
          name "/echo"
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port/echo"
            "${SemanticAttributes.HTTP_METHOD.key}" "POST"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 202
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          name EchoHandlerFunction.getSimpleName() + ".handle"
          kind INTERNAL
          childOf span(0)
          attributes {
            "spring-webflux.handler.type" { String tagVal ->
              return tagVal.contains(EchoHandlerFunction.getName())
            }
          }
        }
        span(2) {
          name "echo"
          childOf span(1)
          attributes {
          }
        }
      }
    }
  }

  @Unroll
  def "GET to bad endpoint #testName"() {
    when:
    def response = client.get(urlPath).aggregate().join()

    then:
    response.status().code() == 500
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          name urlPathWithVariables
          kind SERVER
          status ERROR
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port$urlPath"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 500
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          if (annotatedMethod == null) {
            // Functional API
            nameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          } else {
            // Annotation API
            name TestController.getSimpleName() + "." + annotatedMethod
          }
          kind INTERNAL
          childOf span(0)
          status ERROR
          errorEvent(IllegalStateException, "bad things happen")
          attributes {
            if (annotatedMethod == null) {
              // Functional API
              "spring-webflux.handler.type" { String tagVal ->
                return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
              }
            } else {
              // Annotation API
              "spring-webflux.handler.type" TestController.getName()
            }
          }
        }
      }
    }

    where:
    testName                   | urlPath             | urlPathWithVariables   | annotatedMethod
    "functional API fail fast" | "/greet-failfast/1" | "/greet-failfast/{id}" | null
    "functional API fail Mono" | "/greet-failmono/1" | "/greet-failmono/{id}" | null

    "annotation API fail fast" | "/foo-failfast/1"   | "/foo-failfast/{id}"   | "getFooFailFast"
    "annotation API fail Mono" | "/foo-failmono/1"   | "/foo-failmono/{id}"   | "getFooFailMono"
  }

  def "Redirect test"() {
    setup:
    String finalUrl = "http://localhost:$port/double-greet"

    when:
    def response = client.get("/double-greet-redirect").aggregate().join()

    then:
    response.status().code() == 200
    assertTraces(2) {
      // TODO: why order of spans is different in these traces?
      trace(0, 2) {
        span(0) {
          name "/double-greet-redirect"
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" "http://localhost:$port/double-greet-redirect"
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 307
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          name "RedirectComponent.lambda"
          kind INTERNAL
          childOf span(0)
          attributes {
            "spring-webflux.handler.type" { String tagVal ->
              return (tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
                || tagVal.contains("Lambda"))
            }
          }
        }
      }
      trace(1, 2) {
        span(0) {
          name "/double-greet"
          kind SERVER
          hasNoParent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key}" Long
            "${SemanticAttributes.HTTP_URL.key}" finalUrl
            "${SemanticAttributes.HTTP_METHOD.key}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key}" String
          }
        }
        span(1) {
          nameContains(SpringWebFluxTestApplication.getSimpleName() + "\$", ".handle")
          kind INTERNAL
          childOf span(0)
          attributes {
            "spring-webflux.handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
          }
        }
      }
    }
  }

  @Unroll
  def "Multiple GETs to delaying route #testName"() {
    setup:
    def requestsCount = 50 // Should be more than 2x CPUs to fish out some bugs
    def url = "http://localhost:$port$urlPath"
    when:
    def responses = (0..requestsCount - 1).collect { client.get(urlPath).aggregate().join() }

    then:
    responses.every { it.status().code() == 200 }
    responses.every { it.contentUtf8() == expectedResponseBody }
    assertTraces(responses.size()) {
      responses.eachWithIndex { def response, int i ->
        trace(i, 2) {
          span(0) {
            name urlPathWithVariables
            kind SERVER
            hasNoParent()
            attributes {
              "${SemanticAttributes.NET_PEER_IP.key}" "127.0.0.1"
              "${SemanticAttributes.NET_PEER_PORT.key}" Long
              "${SemanticAttributes.HTTP_URL.key}" url
              "${SemanticAttributes.HTTP_METHOD.key}" "GET"
              "${SemanticAttributes.HTTP_STATUS_CODE.key}" 200
              "${SemanticAttributes.HTTP_FLAVOR.key}" "1.1"
              "${SemanticAttributes.HTTP_USER_AGENT.key}" String
            }
          }
          span(1) {
            if (annotatedMethod == null) {
              // Functional API
              nameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
            } else {
              // Annotation API
              name TestController.getSimpleName() + "." + annotatedMethod
            }
            kind INTERNAL
            childOf span(0)
            attributes {
              if (annotatedMethod == null) {
                // Functional API
                "spring-webflux.handler.type" { String tagVal ->
                  return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
                }
              } else {
                // Annotation API
                "spring-webflux.handler.type" TestController.getName()
              }
            }
          }
        }
      }
    }

    where:
    testName                          | urlPath          | urlPathWithVariables | annotatedMethod | expectedResponseBody
    "functional API delayed response" | "/greet-delayed" | "/greet-delayed"     | null            | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE
    "annotation API delayed response" | "/foo-delayed"   | "/foo-delayed"       | "getFooDelayed" | new FooModel(3L, "delayed").toString()
  }
}
