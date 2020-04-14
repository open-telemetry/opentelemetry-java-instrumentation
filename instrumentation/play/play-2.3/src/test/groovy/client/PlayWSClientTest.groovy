/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package client

import io.opentelemetry.auto.test.base.HttpClientTest
import play.GlobalSettings
import play.libs.ws.WS
import play.test.FakeApplication
import play.test.Helpers
import spock.lang.Shared

import java.util.concurrent.TimeUnit

class PlayWSClientTest extends HttpClientTest {
  @Shared
  def application = new FakeApplication(
    new File("."),
    FakeApplication.getClassLoader(),
    [
      "ws.timeout.connection": CONNECT_TIMEOUT_MS,
      "ws.timeout.request"   : READ_TIMEOUT_MS
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
  boolean testRedirects() {
    false
  }

  @Override
  boolean testConnectionFailure() {
    false
  }

  @Override
  boolean testRemoteConnection() {
    // On connection failures the operation and resource names end up different from expected.
    // This would require a lot of changes to the base client test class to support
    // span.operationName = "netty.connect"
    // span.resourceName = "netty.connect"
    false
  }
}
