/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.CompletionStage
import java.util.function.Consumer
import play.libs.ws.WS
import play.libs.ws.WSResponse
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Subject
import spock.lang.Timeout
// Play 2.6+ uses a separately versioned client that shades the underlying dependency
// This means our built in instrumentation won't work.
@Timeout(5)
class PlayWsClientTest extends HttpClientTest implements AgentTestTrait {
  @Subject
  @Shared
  @AutoCleanup
  def client = WS.newClient(-1)

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers) {
    return sendRequest(method, uri, headers).toCompletableFuture().get().status
  }

  @Override
  void doRequestAsync(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback) {
    sendRequest(method, uri, headers).thenAccept {
      callback.accept(it.status)
    }
  }

  private CompletionStage<WSResponse> sendRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.url(uri.toString())
    headers.entrySet().each {
      request.setHeader(it.key, it.value)
    }

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
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    return false
  }

}
