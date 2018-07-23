import akka.actor.ActorSystem
import akka.http.javadsl.Http
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.japi.Pair
import akka.stream.ActorMaterializer
import akka.stream.StreamTcpException
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.RatpackUtils
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import scala.util.Try
import spock.lang.Shared

import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutionException

import static datadog.trace.agent.test.ListWriterAssert.assertTraces
import static ratpack.groovy.test.embed.GroovyEmbeddedApp.ratpack

class AkkaHttpClientInstrumentationTest extends AgentTestRunner {
  static {
    System.setProperty("dd.integration.akka-http-client.enabled", "true")
  }

  private static final String MESSAGE = "an\nmultiline\nhttp\nresponse"
  private static final long TIMEOUT = 10000L

  @Shared
  def server = ratpack {
    handlers {
      prefix("success") {
        all {
          RatpackUtils.handleDistributedRequest(context)

          response.status(200).send(MESSAGE)
        }
      }

      prefix("error") {
        all {
          RatpackUtils.handleDistributedRequest(context)

          throw new RuntimeException("error")
        }
      }
    }
  }

  @Shared
  ActorSystem system = ActorSystem.create()
  @Shared
  ActorMaterializer materializer = ActorMaterializer.create(system)

  def pool = Http.get(system).<Integer>superPool(materializer)

  def "#route request trace" () {
    setup:
    def url = server.address.resolve("/" + route).toURL()

    HttpRequest request = HttpRequest.create(url.toString())
    CompletionStage<HttpResponse> responseFuture =
      Http.get(system)
        .singleRequest(request, materializer)

    when:
    HttpResponse response = responseFuture.toCompletableFuture().get()
    String message = readMessage(response)

    then:
    response.status().intValue() == expectedStatus
    if (expectedMessage != null) {
      message == expectedMessage
    }

    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[1][0])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /$route"
          errored expectedError
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" expectedStatus
            "$Tags.HTTP_URL.key" "${server.address}$route"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT.key" "akka-http-client"
            if (expectedError) {
              "$Tags.ERROR.key" true
            }
          }
        }
      }
    }

    where:
    route     | expectedStatus | expectedError | expectedMessage
    "success" | 200            | false         | MESSAGE
    "error"   | 500            | true          | null
  }

  def "error request trace" () {
    setup:
    def url = new URL("http://localhost:${server.address.port + 1}/test")

    HttpRequest request = HttpRequest.create(url.toString())
    CompletionStage<HttpResponse> responseFuture =
      Http.get(system)
        .singleRequest(request, materializer)

    when:
    responseFuture.toCompletableFuture().get()

    then:
    thrown ExecutionException
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /test"
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_URL.key" url.toString()
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT.key" "akka-http-client"
            "$Tags.ERROR.key" true
            errorTags(StreamTcpException, { it.contains("Tcp command") })
          }
        }
      }
    }
  }

  def "singleRequest exception trace" () {
    when:
    // Passing null causes NPE in singleRequest
    Http.get(system).singleRequest(null, materializer)

    then:
    thrown NullPointerException
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "akka-http.request"
          errored true
          tags {
            defaultTags()
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT.key" "akka-http-client"
            "$Tags.ERROR.key" true
            errorTags(NullPointerException)
          }
        }
      }
    }

  }


  def "#route pool request trace" () {
    setup:
    def url = server.address.resolve("/" + route).toURL()

    CompletionStage<Pair<Try<HttpResponse>, Integer>> sink = Source
      .<Pair<HttpRequest, Integer>>single(new Pair(HttpRequest.create(url.toString()), 1))
      .via(pool)
      .runWith(Sink.<Pair<Try<HttpResponse>, Integer>>head(), materializer)

    when:
    HttpResponse response = sink.toCompletableFuture().get().first().get()
    String message = readMessage(response)

    then:
    response.status().intValue() == expectedStatus
    if (expectedMessage != null) {
      message == expectedMessage
    }

    assertTraces(TEST_WRITER, 2) {
      trace(0, 1) {
        span(0) {
          operationName "test-http-server"
          childOf(TEST_WRITER[1][0])
          errored false
          tags {
            defaultTags()
          }
        }
      }
      trace(1, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /$route"
          errored expectedError
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" expectedStatus
            "$Tags.HTTP_URL.key" "${server.address}$route"
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT.key" "akka-http-client"
            if (expectedError) {
              "$Tags.ERROR.key" true
            }
          }
        }
      }
    }

    where:
    route     | expectedStatus | expectedError | expectedMessage
    "success" | 200            | false         | MESSAGE
    "error"   | 500            | true          | null
  }

  def "error request pool trace" () {
    setup:
    def url = new URL("http://localhost:${server.address.port + 1}/test")

    CompletionStage<Pair<Try<HttpResponse>, Integer>> sink = Source
      .<Pair<HttpRequest, Integer>>single(new Pair(HttpRequest.create(url.toString()), 1))
      .via(pool)
      .runWith(Sink.<Pair<Try<HttpResponse>, Integer>>head(), materializer)
    def response = sink.toCompletableFuture().get().first()

    when:
    response.get()

    then:
    thrown StreamTcpException
    assertTraces(TEST_WRITER, 1) {
      trace(0, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "akka-http.request"
          resourceName "GET /test"
          errored true
          tags {
            defaultTags()
            "$Tags.HTTP_URL.key" url.toString()
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
            "$Tags.COMPONENT.key" "akka-http-client"
            "$Tags.ERROR.key" true
            errorTags(StreamTcpException, { it.contains("Tcp command") })
          }
        }
      }
    }
  }

  String readMessage(HttpResponse response) {
    response.entity().toStrict(TIMEOUT, materializer).toCompletableFuture().get().getData().utf8String()
  }

}
