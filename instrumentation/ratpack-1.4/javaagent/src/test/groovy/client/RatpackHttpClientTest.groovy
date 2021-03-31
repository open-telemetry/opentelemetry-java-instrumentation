/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package client

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpClientTest
import java.time.Duration
import java.util.function.Consumer
import ratpack.exec.ExecResult
import ratpack.http.client.HttpClient
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Timeout

@Timeout(5)
class RatpackHttpClientTest extends HttpClientTest implements AgentTestTrait {

  @AutoCleanup
  @Shared
  ExecHarness exec = ExecHarness.harness()

  @Shared
  def client = HttpClient.of {
    it.readTimeout(Duration.ofSeconds(2))
    // Connect timeout added in 1.5
  }

  @Override
  int doRequest(String method, URI uri, Map<String, String> headers = [:], Consumer<Integer> callback = null) {
    ExecResult<Integer> result = exec.yield {
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
        callback?.accept(it.status.code)
        it.status.code
      }
    }
    return result.value
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
    return false
  }
}
