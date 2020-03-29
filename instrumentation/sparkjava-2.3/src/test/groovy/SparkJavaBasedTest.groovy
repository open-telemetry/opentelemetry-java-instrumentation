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
import io.opentelemetry.auto.instrumentation.api.MoreTags
import io.opentelemetry.auto.instrumentation.api.Tags
import io.opentelemetry.auto.test.AgentTestRunner
import io.opentelemetry.auto.test.utils.OkHttpUtils
import io.opentelemetry.auto.test.utils.PortUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import spark.Spark
import spock.lang.Shared

import static io.opentelemetry.trace.Span.Kind.SERVER

class SparkJavaBasedTest extends AgentTestRunner {

  static {
    System.setProperty("ota.integration.jetty.enabled", "true")
    System.setProperty("ota.integration.sparkjava.enabled", "true")
  }

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
    response.body().string() == "Hello asdf1234"

    assertTraces(1) {
      trace(0, 1) {
        span(0) {
          operationName "/param/:param"
          spanKind SERVER
          errored false
          parent()
          tags {
            "$MoreTags.NET_PEER_IP" "127.0.0.1"
            "$MoreTags.NET_PEER_PORT" Long
            "$Tags.HTTP_URL" "http://localhost:$port/param/asdf1234"
            "$Tags.HTTP_METHOD" "GET"
            "$Tags.HTTP_STATUS" 200
            "span.origin.type" spark.embeddedserver.jetty.JettyHandler.name
          }
        }
      }
    }
  }

}
