/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.time.Duration
import java.util.function.Consumer
import ratpack.exec.Operation
import ratpack.exec.Promise
import ratpack.http.client.HttpClient
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
  }

  @Override
  Void buildRequest(String method, URI uri, Map<String, String> headers) {
    return null
  }

  @Override
  int sendRequest(Void request, String method, URI uri, Map<String, String> headers) {
    return exec.yield {
      internalSendRequest(method, uri, headers)
    }.value
  }

  @Override
  void sendRequestWithCallback(Void request, String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    exec.execute(Operation.of {
      internalSendRequest(method, uri, headers).result {
        callback.accept(it.value)
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
  boolean testRedirects() {
    false
  }

  @Override
  boolean testReusedRequest() {
    // these tests will pass, but they don't really test anything since REQUEST is Void
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }
}
