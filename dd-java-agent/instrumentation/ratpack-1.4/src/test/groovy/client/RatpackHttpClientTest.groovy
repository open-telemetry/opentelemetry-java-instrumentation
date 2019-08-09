package client

import datadog.trace.agent.test.base.HttpClientTest
import datadog.trace.instrumentation.netty41.client.NettyHttpClientDecorator
import ratpack.exec.ExecResult
import ratpack.http.client.HttpClient
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Shared

class RatpackHttpClientTest extends HttpClientTest<NettyHttpClientDecorator> {

  @AutoCleanup
  @Shared
  ExecHarness exec = ExecHarness.harness()

  @Shared
  def client = HttpClient.of {}

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    ExecResult<Integer> result = exec.yield {
      def resp = client.request(uri) { spec ->
        spec.method(method)
        spec.headers { headersSpec ->
          headers.entrySet().each {
            headersSpec.add(it.key, it.value)
          }
        }
      }
      return resp.map {
        callback?.call()
        it.status.code
      }
    }
    return result.value
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
