/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest
import io.opentelemetry.instrumentation.testing.junit.http.SingleConnection
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import play.libs.ws.WS
import play.libs.ws.WSRequest
import play.libs.ws.WSResponse
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Subject

import java.util.concurrent.CompletionStage

// Play 2.6+ uses a separately versioned client that shades the underlying dependency
// This means our built in instrumentation won't work.
class PlayWsClientTest extends HttpClientTest<WSRequest> implements AgentTestTrait {
  @Subject
  @Shared
  @AutoCleanup
  def client = WS.newClient(-1)

  @Override
  WSRequest buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.url(uri.toString())
    headers.entrySet().each {
      request.setHeader(it.key, it.value)
    }
    return request
  }

  @Override
  int sendRequest(WSRequest request, String method, URI uri, Map<String, String> headers) {
    return internalSendRequest(request, method).toCompletableFuture().get().status
  }

  @Override
  void sendRequestWithCallback(WSRequest request, String method, URI uri, Map<String, String> headers, AbstractHttpClientTest.RequestResult requestResult) {
    internalSendRequest(request, method).whenComplete { response, throwable ->
      requestResult.complete({ response.status }, throwable)
    }
  }

  private static CompletionStage<WSResponse> internalSendRequest(WSRequest request, String method) {
    return request.execute(method)
  }

  //TODO see https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/2347
//  @Override
//  String userAgent() {
//    return "AHC"
//  }

  @Override
  boolean testRedirects() {
    false
  }

  @Override
  Set<AttributeKey<?>> httpAttributes(URI uri) {
    Set<AttributeKey<?>> extra = [
      SemanticAttributes.HTTP_SCHEME,
      SemanticAttributes.HTTP_TARGET
    ]
    super.httpAttributes(uri) + extra
  }

  @Override
  SingleConnection createSingleConnection(String host, int port) {
    // Play HTTP client uses AsyncHttpClient internally which does not support HTTP 1.1 pipelining
    // nor waiting for connection pool slots to free up. Therefore making a single connection test
    // would require manually sequencing the connections, which is not meaningful for a high
    // concurrency test.
    return null
  }
}
