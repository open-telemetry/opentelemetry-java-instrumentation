import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.utils.PortUtils
import datadog.trace.api.DDSpanTypes
import io.netty.channel.AbstractChannel
import io.opentracing.tag.Tags
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientRequest
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import spock.lang.AutoCleanup
import spock.lang.Shared

import java.util.concurrent.CompletableFuture

import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer
import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace

class VertxHttpClientTest extends AgentTestRunner {

  private static final String MESSAGE = "hello world"

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        handleDistributedRequest()

        response.status(200).send(MESSAGE)
      }

      prefix("error") {
        handleDistributedRequest()

        throw new RuntimeException("error")
      }
    }
  }

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  HttpClient httpClient = vertx.createHttpClient()

  def "#route request trace"() {
    setup:
    def responseFuture = new CompletableFuture<HttpClientResponse>()
    def messageFuture = new CompletableFuture<String>()
    httpClient.getNow(server.address.port, server.address.host, "/" + route, { response ->
      responseFuture.complete(response)
      response.bodyHandler({ buffer ->
        messageFuture.complete(buffer.toString())
      })
    })

    when:
    HttpClientResponse response = responseFuture.get()
    String message = messageFuture.get()

    then:
    response.statusCode() == expectedStatus
    if (expectedMessage != null) {
      message == expectedMessage
    }

    assertTraces(2) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[1][0])
      trace(1, 1) {
        span(0) {
          parent()
          serviceName "unnamed-java-app"
          operationName "netty.client.request"
          resourceName "GET /$route"
          spanType DDSpanTypes.HTTP_CLIENT
          errored expectedError
          tags {
            defaultTags()
            "$Tags.HTTP_STATUS.key" expectedStatus
            "$Tags.HTTP_URL.key" "${server.address}/$route"
            "$Tags.PEER_HOSTNAME.key" server.address.host
            "$Tags.PEER_HOST_IPV4.key" "127.0.0.1"
            "$Tags.PEER_PORT.key" server.address.port
            "$Tags.HTTP_METHOD.key" "GET"
            "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
            "$Tags.COMPONENT.key" "netty-client"
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

  def "test connection failure"() {
    setup:
    def invalidPort = PortUtils.randomOpenPort()

    def errorFuture = new CompletableFuture<Throwable>()

    runUnderTrace("parent") {
      HttpClientRequest request = httpClient.request(
        HttpMethod.GET,
        invalidPort,
        server.address.host,
        "/",
        { response ->
          // We expect to never get here since our request is expected to fail
          errorFuture.complete(null)
        })
      request.exceptionHandler({ error ->
        errorFuture.complete(error)
      })
      request.end()
    }

    when:
    def throwable = errorFuture.get()

    then:
    throwable.cause instanceof ConnectException

    and:
    assertTraces(1) {
      trace(0, 2) {
        span(0) {
          operationName "parent"
          parent()
        }
        span(1) {
          operationName "netty.connect"
          resourceName "netty.connect"
          childOf span(0)
          errored true
          tags {
            "$Tags.COMPONENT.key" "netty"
            errorTags AbstractChannel.AnnotatedConnectException, "Connection refused: localhost/127.0.0.1:$invalidPort"
            defaultTags()
          }
        }
      }
    }
  }
}
