/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan
import static io.opentelemetry.instrumentation.test.utils.TraceUtils.runUnderTrace
import static org.junit.Assume.assumeTrue

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.http.HttpClient
import io.vertx.core.http.HttpClientOptions
import io.vertx.core.http.HttpClientResponse
import io.vertx.core.http.HttpMethod
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Timeout

class VertxHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @Shared
  def vertx = Vertx.vertx(new VertxOptions())
  @Shared
  def clientOptions = new HttpClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS)
  @Shared
  def httpClient = vertx.createHttpClient(clientOptions)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers, Closure callback) {
    return doRequest(httpClient, method, uri, headers, callback)
  }

  int doRequest(HttpClient client, String method, URI uri, Map<String, String> headers, Closure callback = null) {
    CompletableFuture<HttpClientResponse> future = new CompletableFuture<>()
    def request = client.request(HttpMethod.valueOf(method), uri.port, uri.host, "$uri")
    headers.each { request.putHeader(it.key, it.value) }
    request.handler { response ->
      callback?.call()
      future.complete(response)
      println headers.get("test-request-id") + " -- " + response.headers().get("test-request-id")
    }
    request.end()

    return future.get().statusCode()
  }


  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  boolean testRemoteConnection() {
    // FIXME: figure out how to configure timeouts.
    false
  }

  private HttpClient connect(URI uri) {
    HttpClientOptions clientOptions = new HttpClientOptions().setConnectTimeout(CONNECT_TIMEOUT_MS).setMaxPoolSize(1).setKeepAlive(true).setPipelining(true)
    return vertx.createHttpClient(clientOptions)
  }

  def "high concurrency test over single connection"() {
    setup:
    assumeTrue(testCausality())
    int count = 50
    def method = 'GET'
    def url = server.address.resolve("/success")
    def latch = new CountDownLatch(1)
    def pool = Executors.newFixedThreadPool(4)
    HttpClient connection = connect(url)

    when:
    count.times { index ->
      def job = {
        latch.await()
        runUnderTrace("Parent span " + index) {
          Span.current().setAttribute("test.request.id", index)
          doRequest(connection, method, url, ["test-request-id": index.toString()])
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

}
