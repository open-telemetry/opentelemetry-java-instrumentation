/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.netty.channel.ConnectTimeoutException
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.SpanAssert
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.time.Duration
import ratpack.exec.Operation
import ratpack.exec.Promise
import ratpack.http.client.HttpClient
import ratpack.http.client.HttpClientSpec
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Shared

class RatpackHttpClientTest extends HttpClientTest<Void> implements AgentTestTrait {

  @AutoCleanup
  @Shared
  ExecHarness exec = ExecHarness.harness()

  @Shared
  def client = HttpClient.of {
    it.readTimeout(Duration.ofSeconds(2))
    // Connect timeout added in 1.5
    // execController method added in 1.9
    if (HttpClientSpec.metaClass.getMetaMethod("execController") != null) {
      it.execController(exec.getController())
    }
    configureClient(it)
  }

  void configureClient(HttpClientSpec spec) {
  }

  @Override
  Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  int sendRequest(Void request, String method, URI uri, Map<String, String> headers) {
    return exec.yield {
      internalSendRequest(method, uri, headers)
    }.valueOrThrow
  }

  @Override
  void sendRequestWithCallback(Void request, String method, URI uri, Map<String, String> headers, RequestResult requestResult) {
    exec.execute(Operation.of {
      internalSendRequest(method, uri, headers).result {result ->
        requestResult.complete({ result.value }, result.throwable)
      }
    })
  }

  // overridden in RatpackForkedHttpClientTest
  Promise<Integer> internalSendRequest(String method, URI uri, Map<String, String> headers) {
    def resp = client.request(uri) { spec ->
      spec.connectTimeout(Duration.ofSeconds(2))
      spec.method(method)
      spec.headers { headersSpec ->
        headers.entrySet().each {
          headersSpec.add(it.key, it.value)
        }
      }
    }
    return resp.map {
      it.status.code
    }
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://www.google.com:81/": // dropped request
      case "https://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  void assertClientSpanErrorEvent(SpanAssert spanAssert, URI uri, Throwable exception) {
    switch (uri.toString()) {
      case "http://www.google.com:81/": // dropped request
      case "https://192.0.2.1/": // non routable address
        spanAssert.errorEvent(ConnectTimeoutException, ~/connection timed out:/)
        return
    }
    super.assertClientSpanErrorEvent(spanAssert, uri, exception)
  }

  @Override
  boolean hasClientSpanHttpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "http://www.google.com:81/": // dropped request
      case "https://192.0.2.1/": // non routable address
        return false
      default:
        return true
    }
  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  boolean testReusedRequest() {
    // these tests will pass, but they don't really test anything since REQUEST is Void
    false
  }

  @Override
  boolean testHttps() {
    false
  }
}
