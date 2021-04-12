/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import play.GlobalSettings
import play.libs.F
import play.libs.ws.WS
import play.libs.ws.WSClient
import play.libs.ws.WSRequestHolder
import play.libs.ws.WSResponse
import play.test.FakeApplication
import play.test.Helpers
import spock.lang.Shared

class PlayWsClientTest extends HttpClientTest<WSRequestHolder> implements AgentTestTrait {
  @Shared
  def application = new FakeApplication(
    new File("."),
    FakeApplication.getClassLoader(),
    [
      "ws.timeout.connection": CONNECT_TIMEOUT_MS
    ],
    Collections.emptyList(),
    new GlobalSettings()
  )

  @Shared
  WSClient client

  def setupSpec() {
    Helpers.start(application)
    client = WS.client()
  }

  def cleanupSpec() {
    Helpers.stop(application)
  }

  @Override
  WSRequestHolder buildRequest(String method, URI uri, Map<String, String> headers) {
    def request = client.url(uri.toString())
    headers.entrySet().each {
      request.setHeader(it.key, it.value)
    }
    return request
  }

  @Override
  int sendRequest(WSRequestHolder request, String method, URI uri, Map<String, String> headers) {
    return internalSendRequest(request, method).get(1, TimeUnit.SECONDS).status
  }

  @Override
  void sendRequestWithCallback(WSRequestHolder request, String method, URI uri, Map<String, String> headers, Consumer<Integer> callback) {
    internalSendRequest(request, method).onRedeem {
      callback.accept(it.status)
    }
  }

  private static F.Promise<WSResponse> internalSendRequest(WSRequestHolder request, String method) {
    return request.execute(method)
  }

  @Override
  String userAgent() {
    return "NING"
  }

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
    // On connection failures the operation and span names end up different from expected.
    // This would require a lot of changes to the base client test class to support
    // span.spanName = "netty.connect"
    false
  }
}
