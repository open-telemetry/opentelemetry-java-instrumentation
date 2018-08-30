import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.OkHttpUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import datadog.trace.instrumentation.springwebflux.EchoHandlerFunction
import datadog.trace.instrumentation.springwebflux.FooModel
import datadog.trace.instrumentation.springwebflux.SpringWebFluxTestApplication
import datadog.trace.instrumentation.springwebflux.TestController
import io.opentracing.tag.Tags
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.web.server.ResponseStatusException
import spock.lang.Unroll

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = SpringWebFluxTestApplication)
class SpringWebfluxTest extends AgentTestRunner {

  static final okhttp3.MediaType PLAIN_TYPE = okhttp3.MediaType.parse("text/plain; charset=utf-8")
  static final String INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX = SpringWebFluxTestApplication.getName() + "\$"
  static final String SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX = SpringWebFluxTestApplication.getSimpleName() + "\$"

  @LocalServerPort
  private int port

  OkHttpClient client = OkHttpUtils.client()

  @Unroll
  def "Basic GET test #testName to functional API"() {
    setup:
    String url = "http://localhost:$port/greet$urlSuffix"
    def request = new Request.Builder().url(url).get().build()
    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    response.body().string() == expectedResponseBody
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(GET && /greet$pathVariableUrlSuffix)"
            "handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /greet$pathVariableUrlSuffix"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
      }
    }

    where:
    testName              | urlSuffix      | pathVariableUrlSuffix | expectedResponseBody
    "without paramaters"  | ""             | ""                    | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE
    "with one parameter"  | "/WORLD"       | "/{name}"             | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " WORLD"
    "with two parameters" | "/World/Test1" | "/{name}/{word}"      | SpringWebFluxTestApplication.GreetingHandler.DEFAULT_RESPONSE + " World Test1"
  }

  @Unroll
  def "Basic GET test #testName to annotations API"() {
    setup:
    String url = "http://localhost:$port/foo$urlSuffix"
    def request = new Request.Builder().url(url).get().build()
    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    response.body().string() == expectedResponseBody
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName TestController.getSimpleName() + ".getFooModel"
          operationName TestController.getSimpleName() + ".getFooModel"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "handler.type" TestController.getName()
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /foo$pathVariableUrlSuffix"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
      }
    }

    where:
    testName              | urlSuffix  | pathVariableUrlSuffix | expectedResponseBody
    "without parameters"  | ""         | ""                    | new FooModel(0L, "DEFAULT").toString()
    "with one parameter"  | "/1"       | "/{id}"               | new FooModel(1, "pass").toString()
    "with two parameters" | "/2/world" | "/{id}/{name}"        | new FooModel(2, "world").toString()
  }

  def "404 GET test"() {
    setup:
    String url = "http://localhost:$port/notfoundgreet"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 404
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName "404"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 404
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
        span(1) {
          resourceName "DispatcherHandler.handle"
          operationName "DispatcherHandler.handle"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            errorTags(ResponseStatusException, String)
            defaultTags()
          }
        }
      }
    }
  }

  def "Basic POST test"() {
    setup:
    String echoString = "TEST"
    String url = "http://localhost:$port/echo"
    RequestBody body = RequestBody.create(PLAIN_TYPE, echoString)
    def request = new Request.Builder().url(url).post(body).build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 202
    response.body().string() == echoString
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName EchoHandlerFunction.getSimpleName() + ".handle"
          operationName EchoHandlerFunction.getSimpleName() + ".handle"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(POST && /echo)"
            "handler.type" { String tagVal ->
              return tagVal.contains(EchoHandlerFunction.getName())
            }
            defaultTags()
          }
        }
        span(1) {
          resourceName "POST /echo"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 202
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
      }
    }
  }

  def "GET to bad annotation API endpoint"() {
    setup:
    String url = "http://localhost:$port/failfoo/1"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 500
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName "GET /failfoo/{id}"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" url
            "error" true
            defaultTags()
          }
        }
        span(1) {
          resourceName "TestController.getFooModelFail"
          operationName "TestController.getFooModelFail"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "handler.type" TestController.getName()
            errorTags(ArithmeticException, String)
            defaultTags()
          }
        }
      }
    }
  }

  def "POST to bad functional API endpoint"() {
    setup:
    String echoString = "TEST"
    RequestBody body = RequestBody.create(PLAIN_TYPE, echoString)
    String url = "http://localhost:$port/fail-echo"
    def request = new Request.Builder().url(url).post(body).build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 500
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName "POST /fail-echo"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          errored true
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "POST"
            "$Tags.HTTP_STATUS.key" 500
            "$Tags.HTTP_URL.key" url
            "error" true
            defaultTags()
          }
        }
        span(1) {
          resourceNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          errored true
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(POST && /fail-echo)"
            "handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
            errorTags(NullPointerException, String)
            defaultTags()
          }
        }
      }
    }
  }

  def "Redirect test"() {
    setup:
    String url = "http://localhost:$port/double-greet-redirect"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code == 200
    assertTraces(TEST_WRITER, 2) {
      trace(0, 2) {
        span(0) {
          resourceName "GET /double-greet-redirect"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 307
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
        span(1) {
          resourceName "RedirectComponent.lambda"
          operationName "RedirectComponent.lambda"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(0))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(GET && /double-greet-redirect)"
            "handler.type" { String tagVal ->
              return (tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
                || tagVal.contains("Lambda")) // || tagVal.contains("Proxy"))
            }
            defaultTags()
          }
        }
      }
      trace(1, 2) {
        span(0) {
          resourceNameContains(SpringWebFluxTestApplication.getSimpleName() + "\$", ".handle")
          operationNameContains(SpringWebFluxTestApplication.getSimpleName() + "\$", ".handle")
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(GET && /double-greet)"
            "handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /double-greet"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" "http://localhost:$port/double-greet"
            defaultTags()
          }
        }
      }
    }
  }

  @Unroll
  def "Flux x#count GET test with functional API endpoint"() {
    setup:
    String expectedResponseBodyStr = FooModel.createXFooModelsStringFromArray(FooModel.createXFooModels(count))
    String url = "http://localhost:$port/greet-counter/$count"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 200
    expectedResponseBodyStr == response.body().string()
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          operationNameContains(SPRING_APP_CLASS_ANON_NESTED_CLASS_PREFIX, ".handle")
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "request.predicate" "(GET && /greet-counter/{count})"
            "handler.type" { String tagVal ->
              return tagVal.contains(INNER_HANDLER_FUNCTION_CLASS_TAG_PREFIX)
            }
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /greet-counter/{count}"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
      }
    }

    where:
    count << [0, 1, 10]
  }

  @Unroll
  def "Flux x#count GET test with spring annotations endpoint"() {
    setup:
    String expectedResponseBodyStr = FooModel.createXFooModelsStringFromArray(FooModel.createXFooModels(count))
    String url = "http://localhost:$port/annotation-foos/$count"
    def request = new Request.Builder().url(url).get().build()

    when:
    def response = client.newCall(request).execute()

    then:
    response.code() == 200
    expectedResponseBodyStr == response.body().string()
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          resourceName TestController.getSimpleName() + ".getXFooModels"
          operationName TestController.getSimpleName() + ".getXFooModels"
          spanType DDSpanTypes.HTTP_SERVER
          childOf(span(1))
          tags {
            "$Tags.COMPONENT.key" "spring-webflux-controller"
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "handler.type" TestController.getName()
            defaultTags()
          }
        }
        span(1) {
          resourceName "GET /annotation-foos/{count}"
          operationName "netty.request"
          spanType DDSpanTypes.HTTP_SERVER
          parent()
          tags {
            "$Tags.COMPONENT.key" "netty"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_SERVER
            "$Tags.PEER_HOSTNAME.key" "localhost"
            "$Tags.PEER_PORT.key" Integer
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.HTTP_STATUS.key" 200
            "$Tags.HTTP_URL.key" url
            defaultTags()
          }
        }
      }
    }

    where:
    count << [0, 1, 10]
  }
}
