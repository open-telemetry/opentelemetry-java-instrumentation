/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */


import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import reactor.netty.http.client.HttpClient
import reactor.netty.http.client.HttpClientResponse

class ReactorNettyHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    false
  }

  @Override
  String userAgent() {
    return "ReactorNetty"
  }
  
  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Closure callback = null) {
    HttpClientResponse resp = HttpClient.create()
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      .baseUrl(server.address.toString())
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
      .response()
      .block()
    if (callback != null) {
      callback.call()
    }
    return resp.status().code()
  }

  int doRequest(HttpClient connection, String method, URI uri, Map<String, String> headers = [:]) {
    HttpClientResponse resp = connection
      .followRedirect(true)
      .headers({ h -> headers.each { k, v -> h.add(k, v) } })
      ."${method.toLowerCase()}"()
      .uri(uri.toString())
      .response()
      .block()
    return resp.status().code()
  }


  def "high concurrency test over single connection"() {
    setup:
    assumeTrue(testCausality())
    int count = 2
    def method = 'GET'
    def url = server.address.resolve("/success")
    def latch = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(4)
    HttpClient httpClient = connect()

    when:
    count.times { index ->
      def job = {
        latch.await()
        runUnderTrace("Parent span " + index) {
          Span.current().setAttribute("test.request.id", index)
          doRequest(httpClient, method, url, ["test-request-id": index.toString()])
        }
      }
      pool.submit(job)
    }
    latch.countDown()

    then:
    assertTraces(count) {
      count.times { idx ->
        trace(idx, 3) {
          def rootSpan = it.span(0)
          //Traces can be in arbitrary order, let us find out the request id of the current one
          def requestId = Integer.parseInt(rootSpan.name.substring("Parent span ".length()))

          basicSpan(it, 0, "Parent span " + requestId, null, null) {
            it."test.request.id" requestId
          }
          clientSpan(it, 1, span(0), method, url)
          serverSpan(it, 2, span(1)) {
            it."test.request.id" requestId
          }
        }
      }
    }

  }

  private HttpClient connect(){
    return HttpClient.newConnection().baseUrl(server.address.toString())
  }

}
