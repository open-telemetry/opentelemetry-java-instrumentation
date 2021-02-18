/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.util.concurrent.TimeUnit
import play.GlobalSettings
import play.libs.ws.WS
import play.test.FakeApplication
import play.test.Helpers
import spock.lang.Shared

class PlayWsClientTest extends HttpClientTest implements AgentTestTrait {
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
  def client

  def setupSpec() {
    Helpers.start(application)
    client = WS.client()
  }

  def cleanupSpec() {
    Helpers.stop(application)
  }

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
