/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.netty.channel.ConnectTimeoutException
import io.netty.handler.timeout.ReadTimeoutException
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.SingleConnection
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import ratpack.exec.Operation
import ratpack.exec.Promise
import ratpack.func.Action
import ratpack.http.client.HttpClient
import ratpack.http.client.HttpClientSpec
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Shared

class RatpackHttpClientTest extends HttpClientTest<Void> implements AgentTestTrait {

  @AutoCleanup
  @Shared
  ExecHarness exec = ExecHarness.harness()

  @AutoCleanup
  @Shared
  def client = buildHttpClient()

  @AutoCleanup
  @Shared
  def singleConnectionClient = buildHttpClient({spec ->
    spec.poolSize(1)
  })

  HttpClient buildHttpClient() {
    return buildHttpClient(null)
  }

  HttpClient buildHttpClient(Action<? super HttpClientSpec> action) {
    HttpClient.of {
      // execController method added in 1.9
      if (HttpClientSpec.metaClass.getMetaMethod("execController") != null) {
        it.execController(exec.getController())
      }
      if (action != null) {
        action.execute(it)
      }
    }
  }

  @Override
  Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  int sendRequest(Void request, String method, URI uri, Map<String, String> headers) {
    return exec.yield {
      internalSendRequest(client, method, uri, headers)
    }.valueOrThrow
  }

  @Override
  void sendRequestWithCallback(Void request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    exec.execute(Operation.of {
      internalSendRequest(client, method, uri, headers).result {result ->
        requestResult.complete({ result.value }, result.throwable)
      }
    })
  }

  // overridden in RatpackForkedHttpClientTest
  Promise<Integer> internalSendRequest(HttpClient client, String method, URI uri, Map<String, String> headers) {
    def resp = client.request(uri) { spec ->
      // Connect timeout for the whole client was added in 1.5 so we need to add timeout for each request
      spec.connectTimeout(Duration.ofSeconds(2))
      if (uri.getPath() == "/read-timeout") {
        spec.readTimeout(Duration.ofMillis(READ_TIMEOUT_MS))
      }
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
  SingleConnection createSingleConnection(String host, int port) {
    return new SingleConnection() {
      @Override
      int doRequest(String path, Map<String, String> headers) throws ExecutionException, InterruptedException, TimeoutException {
        def uri = resolveAddress(path)
        return exec.yield {
          internalSendRequest(singleConnectionClient, "GET", uri, headers)
        }.valueOrThrow
      }
    }
  }

  @Override
  String expectedClientSpanName(URI uri, String method) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return "CONNECT"
      default:
        return super.expectedClientSpanName(uri, method)
    }
  }

  @Override
  Throwable clientSpanError(URI uri, Throwable exception) {
    if (uri.toString() == "https://192.0.2.1/") {
        return new ConnectTimeoutException("connection timed out: /192.0.2.1:443")
    } else if (uri.getPath() == "/read-timeout") {
      return new ReadTimeoutException()
    }
    return exception
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    switch (uri.toString()) {
      case "http://localhost:61/": // unopened port
      case "https://192.0.2.1/": // non routable address
        return []
    }
    return super.httpAttributes(uri)
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
  boolean testReadTimeout() {
    true
  }
}
