import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import spock.lang.Shared
import spock.lang.Timeout

import java.util.concurrent.CompletableFuture

@Timeout(10)
class VertxHttpClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  HttpClient httpClient = vertx.createHttpClient()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    CompletableFuture<HttpClientResponse> future = new CompletableFuture<>()
    def request = httpClient.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    request.handler { response ->
      callback?.call()
      future.complete(response)
    }
    request.end()

    return future.get().statusCode()
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
