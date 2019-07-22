import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpMethod
import io.vertx.reactivex.core.Vertx
import io.vertx.reactivex.ext.web.client.WebClient
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(10)
class VertxRxWebClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @Shared
  Vertx vertx = Vertx.vertx(new VertxOptions())
  @Shared
  WebClient client = WebClient.create(vertx)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    return request
      .rxSend()
      .doOnSuccess { response -> callback?.call() }
      .map { it.statusCode() }
      .toObservable()
      .blockingFirst()
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
