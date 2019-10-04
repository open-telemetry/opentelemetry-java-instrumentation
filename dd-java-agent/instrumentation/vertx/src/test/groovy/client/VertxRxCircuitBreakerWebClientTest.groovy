package client

import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import io.vertx.circuitbreaker.CircuitBreakerOptions
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.reactivex.circuitbreaker.CircuitBreaker
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture

@Timeout(10)
class VertxRxCircuitBreakerWebClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  WebClient client = WebClient.create(vertx)
  @Shared
  CircuitBreaker breaker = CircuitBreaker.create("my-circuit-breaker", vertx,
    new CircuitBreakerOptions()
      .setTimeout(-1) // Disable the timeout otherwise it makes each test take this long.
  )

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }

    def future = new CompletableFuture<Integer>()

    breaker.executeCommand({ command ->
      request.rxSend().doOnSuccess {
        command.complete(it)
      }.doOnError {
        command.fail(it)
      }.subscribe()
    }, {
      callback?.call()
      if (it.succeeded()) {
        future.complete(it.result().statusCode())
      } else {
        future.completeExceptionally(it.cause())
      }
    })
    return future.get()
  }

  @Override
  NettyHttpClientDecorator decorator() {
    return NettyHttpClientDecorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "netty.client.request"
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }
}
