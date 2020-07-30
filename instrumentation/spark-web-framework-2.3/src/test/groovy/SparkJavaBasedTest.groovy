/*
 * Copyright The OpenTelemetry Authors
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

import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import io.opentelemetry.trace.attributes.SemanticAttributes
import okhttp3.OkHttpClient
import okhttp3.Request
import spark.Spark
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.SERVER

class SparkJavaBasedTest extends AgentTestRunner {

  @Shared
  int port

  OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    port = PortUtils.randomOpenPort()
    TestSparkJavaApplication.initSpark(port)
  }

  def cleanupSpec() {
    Spark.stop()
  }

  def "generates spans"() {
    setup:
    def request = new Request.Builder()
      .url("http://localhost:$port/param/asdf1234")
      .get()
      .build()
    def response = client.newCall(request).execute()

    expect:
    port != 0
    def content = response.body.string()
    content == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "/param/:param"
          spanKind SERVER
          errored false
          parent()
          attributes {
            "${SemanticAttributes.NET_PEER_IP.key()}" "127.0.0.1"
            "${SemanticAttributes.NET_PEER_PORT.key()}" Long
            "${SemanticAttributes.HTTP_URL.key()}" "http://localhost:$port/param/asdf1234"
            "${SemanticAttributes.HTTP_RESPONSE_CONTENT_LENGTH.key()}" content.length()
            "${SemanticAttributes.HTTP_METHOD.key()}" "GET"
            "${SemanticAttributes.HTTP_STATUS_CODE.key()}" 200
            "${SemanticAttributes.HTTP_FLAVOR.key()}" "HTTP/1.1"
            "${SemanticAttributes.HTTP_USER_AGENT.key()}" String
            "${SemanticAttributes.HTTP_CLIENT_IP.key()}" "127.0.0.1"
            "servlet.path" ''
          }
        }
      }
    }
  }

}
