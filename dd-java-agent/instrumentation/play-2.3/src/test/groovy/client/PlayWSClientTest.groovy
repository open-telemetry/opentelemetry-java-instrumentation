package client

import datadog.trace.agent.test.base.HttpClientTest
import play.libs.ws.WS
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class PlayWSClientTest extends HttpClientTest {
  @Subject
  @Shared
  @AutoCleanup
  def client = WS.client()

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    def request = client.url(uri.toString())
    headers.entrySet().each {
      request.setHeader(it.key, it.value)
    }

    def status = request.execute(method).map({
      callback?.call()
      it
    }).map({
      it.status
    })
    return status.get(1, TimeUnit.SECONDS)
  }

  @Override
  String component() {
    return "" // NettyHttpClientDecorator.DECORATE.component()
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
